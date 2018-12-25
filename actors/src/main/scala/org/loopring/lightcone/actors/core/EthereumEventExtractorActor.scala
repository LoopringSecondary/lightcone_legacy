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
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
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
      dbModule: DatabaseModule
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
    dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(EthereumEventExtractorActor.name)
    with Stash {

  def ethereumQueryActor: ActorRef = actors.get(EthereumQueryActor.name)
  def ethereumAccessorActor: ActorRef = actors.get(EthereumAccessActor.name)

  def accountManager: ActorRef = actors.get(MultiAccountManagerActor.name)

  val wethAbi = WETHABI()
  val loopringProtocolAbi = LoopringProtocolAbi()

  val zeroAdd: String = "0x" + "0" * 40

  val delegateAddress: String =
    config.getString("loopring_protocol.delegate-address")

  val protocolAddress: String =
    config.getString("loopring_protocol.protocol-address")

  var currentBlockNumber: BigInt = BigInt(-1)
  var currentBlockHash: String = _

  override def preStart(): Unit = {
    self ! XStart()
  }

  override def receive: Receive = {
    case _: XStart ⇒
      for {
        maxBlock: Option[Long] ← dbModule.blockService.findMaxHeight()
        blockNum ← maxBlock match {
          case Some(_) ⇒
            Future.successful(
              Numeric.prependHexPrefix(maxBlock.get.toHexString)
            )
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
        unstashAll()
        context.become(ready)
        self ! IndexNextHeight()
      }
    case _ ⇒
      stash()
  }

  def ready: Receive = {
    case _: IndexNextHeight ⇒
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
          Numeric.prependHexPrefix((currentBlockNumber + 1).toString(16))
        )).mapTo[XGetBlockWithTxObjectByNumberRes]
      else
        Future.successful(None)
    } yield {
      block match {
        case XGetBlockWithTxObjectByNumberRes(_, _, Some(result), _) ⇒
          // 暂时不考虑分叉
//          if (result.parentHash.equals(currentBlockHash)) {
          self ! XBlockJob(
            height = result.number.intValue(),
            hash = result.hash,
            miner = result.miner,
            uncles = result.uncles,
            txs = result.transactions
          )
//          }
//          else
//            self ! XForkBlock((currentBlockNumber - 1).intValue())
        case _ ⇒
          context.system.scheduler
            .scheduleOnce(15 seconds, self, IndexNextHeight())
      }
    }
  }

  // find the fork height
  def handleFork(forkBlock: XForkBlock): Unit = {
    for {
      dbBlockData ← dbModule.blockService
        .findByHeight(forkBlock.height.longValue())
        .map(_.get)
      nodeBlockData ← (ethereumAccessorActor ? XGetBlockWithTxHashByNumberReq(
        Numeric.prependHexPrefix(forkBlock.height.toHexString)
      )).mapTo[XGetBlockWithTxHashByNumberRes]
        .map(_.result.get)
      task ← if (dbBlockData.hash.equals(nodeBlockData.hash)) {
        currentBlockNumber = forkBlock.height
        currentBlockHash = nodeBlockData.hash
        dbModule.blockService
          .obsolete((forkBlock.height + 1).longValue())
          .map(_ ⇒ IndexNextHeight())
      } else {
        Future.successful(XForkBlock((forkBlock.height - 1).intValue()))
      }
    } yield self ! task
  }

  // index block
  def indexBlock(job: XBlockJob): Unit = {
    for {
      txReceipts ← (ethereumAccessorActor ? XBatchGetTransactionReceiptsReq(
        job.txs.map(tx ⇒ XGetTransactionReceiptReq(tx.hash))
      )).mapTo[XBatchGetTransactionReceiptsRes]
        .map(_.resps.map(_.result))
      batchGetUnclesReq = XBatchGetUncleByBlockNumAndIndexReq(
        job.uncles.zipWithIndex.unzip._2.map(
          index ⇒
            XGetUncleByBlockNumAndIndexReq(
              Numeric.prependHexPrefix(job.height.toHexString),
              Numeric.prependHexPrefix(index.toHexString)
            )
        )
      )
      uncles ← (ethereumAccessorActor ? batchGetUnclesReq)
        .mapTo[XBatchGetUncleByBlockNumAndIndexRes]
        .map(_.resps.map(_.result.get.miner))
      addresses = getBalanceAndAllowanceAdds(
        job.txs zip txReceipts,
        Address(delegateAddress),
        Address(protocolAddress)
      )
      fills = getFills(txReceipts)
      filledOrders ← (ethereumQueryActor ? XGetFilledAmountReq(
        fills.map(_.substring(0, 66))
      )).mapTo[XGetFilledAmountRes]
        .map(_.filledAmountSMap)
      submitOrders = getOnlineOrders(txReceipts)
      ordersCancelledEvents = getXOrdersCancelledEvents(txReceipts)
      ordersCutoffEvents = getXOrdersCutoffEvent(txReceipts)
      balanceAddresses = ListBuffer.empty
        .++=(addresses._1)
        .++=(uncles.map(_ → zeroAdd))
        .+=(job.miner → zeroAdd)

      balanceReqs = balanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetBalanceReq(
          add,
          balanceAddresses
            .filter(_._1.equalsIgnoreCase(add))
            .unzip
            ._2
            .toSet
            .toSeq
        )
      })
      allowanceAddresses = addresses._2
      allowanceReqs = allowanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        XGetAllowanceReq(
          add,
          allowanceAddresses
            .filter(_._1.equalsIgnoreCase(add))
            .unzip
            ._2
            .toSet
            .toSeq
        )
      })
      balanceRes ← Future.sequence(
        balanceReqs
          .map(req ⇒ (ethereumQueryActor ? req).mapTo[XGetBalanceRes])
      )
      allowanceRes ← Future.sequence(
        allowanceReqs
          .map(req ⇒ (ethereumQueryActor ? req).mapTo[XGetAllowanceRes])
      )
    } yield {
      if (txReceipts.forall(_.nonEmpty)) {
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
          dbModule.orderCancelledEventService.saveCancelOrder
        )
        ordersCutoffEvents.foreach(dbModule.orderCutoffService.saveCutoff)

        //TODO (yadong) 更新order fill amount--等待MultiAccountManagerActor

        //TODO(yadong) db 存储 online Orders -- 确认要不要定义Order类型

        //db 更新已经处理的最新块
        val blockData = XBlockData(
          hash = job.hash,
          height = job.height,
          avgGasPrice = job.txs.map(_.gasPrice.longValue()).sum / job.txs.size
        )
        dbModule.blockService.saveBlock(blockData)
        currentBlockNumber = job.height
        currentBlockHash = job.hash
        self ! IndexNextHeight()
      } else {
        context.system.scheduler
          .scheduleOnce(1 seconds, self, IndexNextHeight())
      }
    }
  }

  implicit def hex2BigInt(hex: String): BigInt = BigInt(Numeric.toBigInt(hex))

}
