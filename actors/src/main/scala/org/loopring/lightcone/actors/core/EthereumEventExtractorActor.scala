/*
 * Copyright 2018 Loopring Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.dals.{BlockDal, BlockDalImpl}
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import com.google.protobuf.ByteString
import org.loopring.lightcone.persistence.service.{OrdersCancelledEventServiceImpl, OrdersCutoffServiceImpl}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._

// main owner: 李亚东
object EthereumEventExtractorActor {
  val name = "ethereum_event_extractor"

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ XStart(_) ⇒
      ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case XStart(_) ⇒ "address_1"
  }

  def startShardRegion(
    )(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbConfig: DatabaseConfig[JdbcProfile]
    ): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumEventExtractorActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class EthereumEventExtractorActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbConfig: DatabaseConfig[JdbcProfile])
    extends ActorWithPathBasedConfig(EthereumEventExtractorActor.name) {

  def ethereumAccessorActor: ActorRef = actors.get(EthereumAccessActor.name)

  def accountManager: ActorRef = actors.get(AccountManagerActor.name)

  val wethAbi = WETHABI()
  val loopringProtocolAbi = LoopringProtocolAbi()


  val blockDal: BlockDal = new BlockDalImpl()
  val ordersCutoffService = new OrdersCutoffServiceImpl()
  val orderCancelledEventService  = new OrdersCancelledEventServiceImpl()

  val zeroAdd: String = "0x" + "0" * 40
  val delegateAddress: String =
    config.getString("loopring-protocol.delegate-address")

  val protocolAddress: String =
    config.getString("loopring-protocol.protocol-address")

  var currentBlockNumber: BigInt = BigInt(-1)
  var currentBlockHash: String = _

  override def preStart(): Unit = {
    for {
      maxBlock: Option[Long] ← blockDal.findMaxHeight()
      blockNum ← maxBlock match {
        case Some(_) ⇒ Future.successful(BigInt(maxBlock.get))
        case _ ⇒
          (ethereumAccessorActor ? XEthBlockNumberReq())
            .mapTo[XEthBlockNumberRes]
            .map(res ⇒ BigInt(res.result))
      }
      blockHash ← (ethereumAccessorActor ? XGetBlockWithTxHashByNumberReq(
        blockNum.toString(16)
      )).mapTo[XGetBlockWithTxHashByNumberRes]
        .map(_.result.get.hash)
    } yield {
      currentBlockNumber = blockNum
      currentBlockHash = blockHash
    }
  }

  override def receive: Receive = {
    case _: XStart ⇒
      process()
    case forkBlock: XForkBlock ⇒
      handleFork(forkBlock)
    case job: XBlockJob ⇒
      indexBlock(job)
  }

  def process(): Unit = {
    for {
      taskNum ← (ethereumAccessorActor ? XEthBlockNumberReq())
        .mapTo[XEthBlockNumberRes]
        .map(_.result)
      block ← if (taskNum > currentBlockNumber)
        (ethereumAccessorActor ? XGetBlockWithTxObjectByNumberReq(
          (currentBlockNumber + 1).toString(16)
        )).mapTo[XGetBlockWithTxObjectByNumberRes]
      else {
        Future.successful(XGetBlockWithTxObjectByNumberRes())
      }
    } yield {
      block match {
        case XGetBlockWithTxObjectByNumberRes(_, _, Some(result), _) ⇒
          if (result.parentHash.equals(currentBlockHash)) {
            val blockData = XBlockData()
              .withHash(result.hash)
              .withHeight(result.number.longValue())
            blockDal.saveBlock(blockData)
            self ! XBlockJob()
              .withHeight(result.number.intValue())
              .withHash(result.hash)
              .withMiner(result.miner)
              .withUncles(result.uncles)
              .withTxhashes(result.transactions.map(_.hash))
          } else
            self ! XForkBlock((currentBlockNumber - 1).intValue())
        case _ ⇒
          context.system.scheduler.scheduleOnce(15 seconds, self, XStart())
      }
    }
  }

  // find the fork height
  def handleFork(forkBlock: XForkBlock): Unit = {
    for {
      dbBlockData ← blockDal
        .findByHeight(forkBlock.height.longValue())
        .map(_.get)
      nodeBlockData ← (ethereumAccessorActor ? XGetBlockWithTxHashByNumberReq(
        forkBlock.height.toHexString
      )).mapTo[XGetBlockWithTxHashByNumberRes]
        .map(_.result.get)
      task ← if (dbBlockData.hash.equals(nodeBlockData.hash)) {
        currentBlockNumber = forkBlock.height
        currentBlockHash = nodeBlockData.hash
        blockDal.obsolete((forkBlock.height + 1).longValue()).map(_ ⇒ XStart())
      } else {
        Future.successful(XForkBlock((forkBlock.height - 1).intValue()))
      }
    } yield self ! task
  }

  // index block
  def indexBlock(job: XBlockJob): Unit = {
    for {
      txReceipts ← (ethereumAccessorActor ? XBatchGetTransactionReceiptsReq(
        job.txhashes.map(XGetTransactionReceiptReq(_))
      )).mapTo[XBatchGetTransactionReceiptsRes]
        .map(_.resps.map(_.result))
      batchGetUnclesReq = XBatchGetUncleByBlockNumAndIndexReq(
        job.uncles.zipWithIndex.unzip._2.map(
          index ⇒
            XGetUncleByBlockNumAndIndexReq(
              job.height.toHexString,
              index.toHexString
            )
        )
      )
      uncles ← (ethereumAccessorActor ? batchGetUnclesReq)
        .mapAs[XBatchGetUncleByBlockNumAndIndexRes]
        .map(_.resps.map(_.result.get.miner))
      allGet = txReceipts.forall(_.nonEmpty)
      addresses = if (allGet) {
        getBalanceAndAllowanceAdds(txReceipts)
      } else {
        (Seq.empty[(String, String)], Seq.empty[(String, String)])
      }
      fills = if (allGet) {
        getFills(txReceipts)
      } else {
        Seq.empty[String]
      }
      balanceAddresses = ListBuffer
        .empty[(String, String)]
        .++=(addresses._1)
        .++=(uncles.map(_ → zeroAdd))
        .+=(job.miner → zeroAdd)
      balanceReqs = balanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetBalanceReq(
          add,
          balanceAddresses.find(_._1.equalsIgnoreCase(add)).unzip._2.toSet.toSeq
        )
      })
      allowanceAddresses = addresses._2
      allowanceReqs = allowanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetAllowanceReq(
          add,
          allowanceAddresses
            .find(_._1.equalsIgnoreCase(add))
            .unzip
            ._2
            .toSet
            .toSeq
        )
      })
      balanceRes ← Future.sequence(
        balanceReqs
          .map(req ⇒ (ethereumAccessorActor ? req).mapTo[XGetBalanceRes])
      )
      allowanceRes ← Future.sequence(
        allowanceReqs
          .map(req ⇒ (ethereumAccessorActor ? req).mapTo[XGetAllowanceRes])
      )
    } yield {
      if (allGet) {
        balanceRes.foreach(res ⇒ {
          res.balanceMap.foreach(
            item ⇒
              accountManager ! XAddressBalanceUpdated(
                res.address,
                token = item._1,
                balance = item._2
              )
          )
        })
        allowanceRes.foreach(res ⇒ {
          res.allowanceMap.foreach(item ⇒ {
            accountManager ! XAddressAllowanceUpdated(
              res.address,
              token = item._1,
              item._2
            )
          })
        })
        currentBlockNumber = job.height
        currentBlockHash = job.hash
        self ! XStart()
      } else {
        context.system.scheduler.scheduleOnce(30 seconds, self, XStart())
      }
    }
  }

  def getBalanceAndAllowanceAdds(
      receipts: Seq[Option[XTransactionReceipt]]
    ): (Seq[(String, String)], Seq[(String, String)]) = {
    val balanceAddresses = ListBuffer.empty[(String, String)]
    val allowanceAddresses = ListBuffer.empty[(String, String)]
    receipts.foreach(receipt ⇒ {
      balanceAddresses.append(receipt.get.from → zeroAdd)
      balanceAddresses.append(receipt.get.to → zeroAdd)
      receipt.get.logs.foreach(log ⇒ {
        wethAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(transfer: TransferEvent.Result) ⇒
            balanceAddresses.append(
              transfer.sender → log.address,
              transfer.receiver → log.address
            )
            if (receipt.get.to.equalsIgnoreCase(protocolAddress)) {
              allowanceAddresses.append(transfer.sender → log.address)
            }
          case Some(approval: ApprovalEvent.Result) ⇒
            if (approval.spender.equalsIgnoreCase(delegateAddress))
              allowanceAddresses.append(approval.owner → log.address)
          case Some(deposit: DepositEvent.Result) ⇒
            balanceAddresses.append(deposit.dst → log.address)
          case Some(withdrawal: WithdrawalEvent.Result) ⇒
            balanceAddresses.append(withdrawal.src → log.address)
          case _ ⇒
        }
      })
    })
    (balanceAddresses, allowanceAddresses)
  }

  def getFills(receipts: Seq[Option[XTransactionReceipt]]): Seq[String] = {
    val orderHashes = ListBuffer.empty[String]
    receipts
      .foreach(receipt ⇒ {
        receipt.get.logs.foreach { log ⇒
          {
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: RingMinedEvent.Result) ⇒
                orderHashes.append(splitEventToFills(event._fills):_*)
              case _ ⇒
            }
          }
        }
      })
    orderHashes
  }

  def splitEventToFills(_fills: String): Seq[String] = {
    //首先去掉head
    val fillContent = _fills.substring(128)
    val fillLength = 8 * 64
      (0 until (fillContent.length / fillLength))
      .map { index ⇒
        fillContent.substring(index * fillLength, fillLength * (index + 1))
    }
  }



  implicit def hex2BigInt(hex: String): BigInt = BigInt(Numeric.toBigInt(hex))

}
