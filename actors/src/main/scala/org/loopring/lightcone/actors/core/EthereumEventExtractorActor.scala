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
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
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

  def ethereumQueryActor: ActorRef = actors.get(EthereumQueryActor.name)
  def ethereumAccessorActor: ActorRef = actors.get(EthereumAccessActor.name)

  def accountManager: ActorRef = actors.get(AccountManagerActor.name)

  val wethAbi = WETHABI()
  val loopringProtocolAbi = LoopringProtocolAbi()

  val blockDal: BlockDal = new BlockDalImpl()
  val ordersCutoffService = new OrdersCutoffServiceImpl()
  val orderCancelledEventService = new OrdersCancelledEventServiceImpl()

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
        case Some(_) ⇒ Future.successful(maxBlock.get.toHexString)
        case _ ⇒
          (ethereumAccessorActor ? XEthBlockNumberReq())
            .mapTo[XEthBlockNumberRes]
            .map(res ⇒ res.result)
      }
      blockHash ← (ethereumAccessorActor ? XGetBlockWithTxHashByNumberReq(
        blockNum
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
      addresses = getBalanceAndAllowanceAdds(
        txReceipts,
        Address(delegateAddress),
        Address(protocolAddress)
      )
      fills = getFills(txReceipts)
      filledOrders ← (ethereumQueryActor ? GetFilledAmountReq(
        fills.map(_.substring(0, 66))
      )).mapAs[GetFilledAmountRes]
        .map(_.filledAmountSMap)
      ordersCancelledEvents = getXOrdersCancelledEvents(txReceipts)
      ordersCutoffEvents = getXOrdersCutoffEvent(txReceipts)
      balanceAddresses = ListBuffer.empty
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
          .map(req ⇒ (ethereumQueryActor ? req).mapAs[XGetBalanceRes])
      )
      allowanceRes ← Future.sequence(
        allowanceReqs
          .map(req ⇒ (ethereumQueryActor ? req).mapAs[XGetAllowanceRes])
      )
    } yield {
      if (allGet) {
        // 更新余额
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
        // 更新授权
        allowanceRes.foreach(res ⇒ {
          res.allowanceMap.foreach(item ⇒ {
            accountManager ! XAddressAllowanceUpdated(
              res.address,
              token = item._1,
              item._2
            )
          })
        })

        // db 存储订单取消事件
        ordersCancelledEvents.foreach(
          orderCancelledEventService.saveCancelOrder
        )
        ordersCutoffEvents.foreach(ordersCutoffService.saveCutoff)

        //TODO (yadong) 更新order fill amount--等待MultiAccountManagerActor

        //db 更新已经处理的最新块
        val blockData = XBlockData()
          .withHash(job.hash)
          .withHeight(job.height)
        blockDal.saveBlock(blockData)

        currentBlockNumber = job.height
        currentBlockHash = job.hash
        self ! XStart()
      } else {
        context.system.scheduler.scheduleOnce(30 seconds, self, XStart())
      }
    }
  }

  implicit def hex2BigInt(hex: String): BigInt = BigInt(Numeric.toBigInt(hex))

}
