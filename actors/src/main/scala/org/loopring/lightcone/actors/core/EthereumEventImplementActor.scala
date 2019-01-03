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
import akka.cluster.singleton._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.ethereum.event._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._

// main owner: 李亚东
object EthereumEventImplementActor {
  val name = "ethereum_event_implement"

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
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new EthereumEventImplementActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system)
      ),
      name = EthereumEventImplementActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${EthereumEventImplementActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${EthereumEventImplementActor.name}_proxy"
    )
  }
}

class EthereumEventImplementActor(
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

  def ringSettlementActor: ActorRef =
    actors.get(RingSettlementManagerActor.name)
  def orderHandler: ActorRef = actors.get(OrderHandlerActor.name)
  def accountManager: ActorRef = actors.get(MultiAccountManagerActor.name)

  val miners = config
    .getConfig(RingSettlementManagerActor.name)
    .getConfigList("miners")
    .asScala
    .map(config ⇒ Address(config.getString("transaction-origin")).toString)

  val burnRateTiers = config
    .getConfigList("loopring_protocol.burn-rate-table.tiers")
    .asScala
    .map(config ⇒ config.getInt("tier") → config.getInt("rate"))
    .toMap

  val delegateAddress: String =
    config.getString("loopring_protocol.delegate-address")

  val protocolAddress: String =
    config.getString("loopring_protocol.protocol-address")

  var currentBlockNumber: Long = -1
  val taskQueue: mutable.Queue[Long] = new mutable.Queue()

  override def preStart() = {
    self ! Notify("nextBlock")
  }

  override def receive: Receive = {
    case Notify("nextBlock", _) ⇒
      if (taskQueue.nonEmpty) {
        currentBlockNumber = taskQueue.dequeue()
        process()
      } else {
        context.system.scheduler
          .scheduleOnce(15 seconds, self, Notify("nextBlock"))
      }
    case Notify("currentBlock", _) ⇒
      process()
    case job: BlockJob ⇒
      indexBlock(job)
    case BlockImplementTask(heights) ⇒
      taskQueue.enqueue(heights: _*)
  }

  def process(): Unit = {
    for {
      block ← (ethereumAccessorActor ? GetBlockWithTxObjectByNumber.Req(
        Numeric.prependHexPrefix(currentBlockNumber.toHexString)
      )).mapTo[GetBlockWithTxObjectByNumber.Res]
        .map(_.result.get)
    } yield {
      self ! BlockJob(
        height = block.number.longValue(),
        hash = block.hash,
        miner = block.miner,
        uncles = block.uncles,
        txs = block.transactions
      )
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
      ringMinedEvents = getRingMinedEvent(txReceipts)
      trades = getTrades(ringMinedEvents, job.timestamp)
      filledOrders ← (ethereumQueryActor ? GetFilledAmount.Req(
        trades.map(_.orderHash)
      )).mapTo[GetFilledAmount.Res]
        .map(_.filledAmountSMap)
      failedRings = getFailedRings(job.txs zip txReceipts)
      tierUpgradeEvents = getTokenTierUpgradedEvent(txReceipts)
      onlineOrders = getOnlineOrders(txReceipts)
      ordersCancelledEvents = getOrdersCancelledEvents(txReceipts)
      ordersCutoffEvents = getOrdersCutoffEvent(txReceipts)
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
            item ⇒ {
              accountManager ! AddressBalanceUpdated(
                res.address,
                token = item._1,
                balance = item._2
              )
              // 通知RingSettlementManager miner的 余额状态
              if (miners.contains(res.address) && item._1
                    .equals(Address.zeroAddress)) {
                ringSettlementActor ! AddressBalanceUpdated(
                  res.address,
                  token = item._1,
                  balance = item._2
                )
              }
            }
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

        ordersCancelledEvents.foreach(event ⇒ {
          dbModule.orderCancelledEventService.saveCancelOrder(event)
          orderHandler ! CancelOrder
            .Req(id = event.orderHash, owner = event.brokerOrOwner)
        })
        ordersCutoffEvents.foreach(event ⇒ {
          dbModule.orderCutoffService.saveCutoff(event)
          //TODO(yadong) 通知cutOff 到对应的actor
        })

        trades.foreach(
          dbModule.tradeService.saveTrade
        )
        //TODO (yadong) 更新order fill amount--等待MultiAccountManagerActor

        //TODO (yadong) 环路提交环路失败的事件 marketManger

        onlineOrders.foreach(order ⇒ {
          orderHandler ! SubmitOrder.Req(Some(order))
        })

        tierUpgradeEvents.foreach(event ⇒ {
          val rate = burnRateTiers(event.tier.intValue())
          //TODO（yadong）burn rate db 存储
        })

        //db 更新已经处理的最新块
        val blockData = BlockData(
          hash = job.hash,
          height = job.height,
          avgGasPrice = job.txs.map(_.gasPrice.longValue()).sum / job.txs.size
        )
        dbModule.blockService.saveBlock(blockData)
        self ! Notify("nextBlock")
      } else {
        context.system.scheduler
          .scheduleOnce(1 seconds, self, Notify("currentBlock"))
      }
    }
  }

  implicit def hex2BigInt(hex: String): BigInt = BigInt(Numeric.toBigInt(hex))

}
