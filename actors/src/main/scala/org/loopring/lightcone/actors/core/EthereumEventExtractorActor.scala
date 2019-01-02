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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._

// main owner: 李亚东
object EthereumEventExtractorActor {
  val name = "ethereum_event_extractor"

  def start(
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
    system.actorOf(Props(new EthereumEventExtractorActor()))
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

  val delegateAddress: String =
    config.getString("loopring_protocol.delegate-address")

  val protocolAddress: String =
    config.getString("loopring_protocol.protocol-address")

  var currentBlockNumber: BigInt = BigInt(-1)

  override def preStart(): Unit = {
    for {
      maxBlock: Option[Long] ← dbModule.blockService.findMaxHeight()
      blockNum ← maxBlock match {
        case Some(_) ⇒
          Future.successful(
            Numeric.prependHexPrefix(maxBlock.get.toHexString)
          )
        case _ ⇒
          (ethereumAccessorActor ? GetBlockNumber.Req())
            .mapTo[GetBlockNumber.Res]
            .map(res ⇒ res.result)
      }
    } yield {
      currentBlockNumber = blockNum -1
      self ! Notify("nextBlock")
    }
  }

  override def receive: Receive = {
    case Notify("nextBlock", _) ⇒
      process()
    case job: BlockJob ⇒
      indexBlock(job)
  }

  def process(): Unit = {
    for {
      taskNum ← (ethereumAccessorActor ? GetBlockNumber.Req())
        .mapTo[GetBlockNumber.Res]
        .map(_.result)
      block ← if (taskNum > currentBlockNumber)
        (ethereumAccessorActor ? GetBlockWithTxObjectByNumber.Req(
          Numeric.prependHexPrefix((currentBlockNumber + 1).toString(16))
        )).mapTo[GetBlockWithTxObjectByNumber.Res]
      else
        Future.successful(None)
    } yield {
      block match {
        case GetBlockWithTxObjectByNumber.Res(_, _, Some(result), _) ⇒
          self ! BlockJob(
            height = result.number.longValue(),
            hash = result.hash,
            miner = result.miner,
            uncles = result.uncles,
            txs = result.transactions
          )
        case _ ⇒
          context.system.scheduler
            .scheduleOnce(15 seconds, self, Notify("nextBlock"))
      }
    }
  }

  // index block
  def indexBlock(job: BlockJob): Unit = {
    for {
      txReceipts ← (ethereumAccessorActor ? BatchGetTransactionReceipts.Req(
        job.txs.map(tx ⇒ GetTransactionReceipt.Req(tx.hash))
      )).mapTo[BatchGetTransactionReceipts.Res]
        .map(_.resps.map(_.result))
      batchGetUnclesReq = BatchGetUncle.Req(
        job.uncles.zipWithIndex.unzip._2.map(
          index ⇒
            GetUncle.Req(
              Numeric.prependHexPrefix(job.height.toHexString),
              Numeric.prependHexPrefix(index.toHexString)
            )
        )
      )
      uncles ← (ethereumAccessorActor ? batchGetUnclesReq)
        .mapTo[BatchGetUncle.Res]
        .map(_.resps.map(_.result.get.miner))
      addresses = getBalanceAndAllowanceAdds(
        job.txs zip txReceipts,
        Address(delegateAddress),
        Address(protocolAddress)
      )
      fills = getFills(txReceipts)
      filledOrders ← (ethereumQueryActor ? GetFilledAmount.Req(
        fills.map(_.substring(0, 66))
      )).mapTo[GetFilledAmount.Res]
        .map(_.filledAmountSMap)
      submitOrders = getOnlineOrders(txReceipts)
      ordersCancelledEvents = getXOrdersCancelledEvents(txReceipts)
      ordersCutoffEvents = getXOrdersCutoffEvent(txReceipts)
      balanceAddresses = ListBuffer.empty
        .++=(addresses._1)
        .++=(uncles.map(_ → Address.zeroAddress))
        .+=(job.miner → Address.zeroAddress)

      balanceReqs = balanceAddresses.unzip._1.toSet.map((add: String) ⇒ {
        GetBalance.Req(
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
        GetAllowance.Req(
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
          .map(req ⇒ (ethereumQueryActor ? req).mapTo[GetBalance.Res])
      )
      allowanceRes ← Future.sequence(
        allowanceReqs
          .map(req ⇒ (ethereumQueryActor ? req).mapTo[GetAllowance.Res])
      )
    } yield {
      if (txReceipts.forall(_.nonEmpty)) {
        // 更新余额
        balanceRes.foreach(res ⇒ {
          res.balanceMap.foreach(
            item ⇒
              accountManager ! AddressBalanceUpdated(
                res.address,
                token = item._1,
                balance = item._2
              )
          )
        })
        // 更新授权
        allowanceRes.foreach(res ⇒ {
          res.allowanceMap.foreach(item ⇒ {
            accountManager ! AddressAllowanceUpdated(
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
        val blockData = BlockData(
          hash = job.hash,
          height = job.height,
          avgGasPrice = job.txs.map(_.gasPrice.longValue()).sum / job.txs.size
        )
        dbModule.blockService.saveBlock(blockData)
        currentBlockNumber = job.height
        self ! Notify("nextBlock")
      } else {
        context.system.scheduler
          .scheduleOnce(1 seconds, self, Notify("nextBlock"))
      }
    }
  }

  implicit def hex2BigInt(hex: String): BigInt = BigInt(Numeric.toBigInt(hex))

}
