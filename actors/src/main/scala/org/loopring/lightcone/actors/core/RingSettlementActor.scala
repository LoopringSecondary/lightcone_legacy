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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import org.loopring.lightcone.ethereum.data.Address

import scala.concurrent._

// main owner: 李亚东
object RingSettlementActor extends ShardedEvenly {
  val name = "ring_settlement"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new RingSettlementActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class RingSettlementActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends Actor
    with Stash
    with ActorLogging
    with RepeatedJobActor {

  //防止一个tx中的订单过多，超过 gaslimit
  private val maxRingsInOneTx = 10
  implicit val ringContext: XRingBatchContext =
    XRingBatchContext(
      lrcAddress = config.getString("ring_settlement.lrc-address"),
      feeRecipient = config.getString("ring_settlement.fee-recipient"),
      miner = config.getString("miner"),
      transactionOrigin = config.getString("transaction-origin"),
      minerPrivateKey = config.getString("miner-privateKey")
    )
  implicit val credentials: Credentials =
    Credentials.create(config.getString("transaction-origin-private-key"))

  val protocolAddress: String =
    config.getString("loopring_protocol.protocol-address")

  val repeatedJobs = Seq(
    Job(
      name = config.getString("ring_settlement.job.name"),
      dalayInSeconds = config.getInt("ring_settlement.job.delay-in-seconds"),
      run = () ⇒ resubmitTx(),
      initialDalayInSeconds =
        config.getInt("ring_settlement.job.initial-delay-in-seconds")
    )
  )

  private val nonce = new AtomicInteger(0)
  private var minerBalance = BigInt(0)

  private def ethereumAccessActor = actors.get(EthereumAccessActor.name)
  private def gasPriceActor = actors.get(GasPriceActor.name)
  private def ringSettlementManagerActor =
    actors.get(RingSettlementManagerActor.name)

  import ethereum._

  override def preStart(): Unit = {
    context.become(starting)
    self ! XStart()
  }

  override def receive: Receive = {
    case _ ⇒
      stash()
  }

  def starting: Receive = {
    case XStart ⇒
      for {
        validNonce ← (ethereumAccessActor ? XGetNonceReq(
          owner = ringContext.transactionOrigin,
          tag = "latest"
        )).mapTo[XGetNonceRes]
          .map(_.result)
        balance ← (ethereumAccessActor ? XEthGetBalanceReq(
          address = ringContext.transactionOrigin,
          tag = "latest"
        )).mapTo[XEthGetBalanceRes]
          .map(_.result)
      } yield {
        minerBalance = BigInt(Numeric.toBigInt(balance))
        nonce.set(Numeric.toBigInt(validNonce).intValue())
        unstashAll()
        context.become(ready)
      }
    case _ ⇒
      stash()
  }

  def ready: Receive = super.receive orElse LoggingReceive {
    case req: XSettleRingsReq =>
      for {
        rawOrders ← Future.sequence(req.rings.map { xOrderRing ⇒
          dbModule.orderService.getOrders(
            Seq(
              xOrderRing.maker.get.order.get.id,
              xOrderRing.taker.get.order.get.id
            )
          )
        })
        xRingBatch = RingBatchGeneratorImpl.generateAndSignRingBatch(rawOrders)
        inputData = RingBatchGeneratorImpl.toSubmitableParamStr(xRingBatch)
        validNonce = nonce.getAndIncrement()
        txData = getSignedTxData(
          inputData,
          validNonce,
          req.gasLimit,
          req.gasPrice,
          to = protocolAddress
        )
        hash ← (ethereumAccessActor ? XSendRawTransactionReq(txData))
          .mapTo[XSendRawTransactionRes]
          .map(_.result)
      } yield {
        //TODO(yadong) 把提交的环路记录到数据库,提交失败以后的处理
        minerBalance = minerBalance.min(
          BigInt(req.gasLimit.toByteArray) * BigInt(req.gasPrice.toByteArray)
        )
        if (minerBalance <= BigInt(5).pow(17)) {
          ringSettlementManagerActor ! XMinerBalanceNotEnough(
            miner = ringContext.transactionOrigin
          )
        }
      }
  }

  //未被提交的交易需要使用新的gas和gasprice重新提交
  def resubmitTx(): Future[Unit] =
    for {
      gasPriceRes <- (gasPriceActor ? XGetGasPriceReq())
        .mapTo[XGetGasPriceRes]
        .map(_.gasPrice)
      //todo：查询数据库等得到未能打块的交易,暂时用XTransaction的结构
      ringTxs = Seq.empty[XTransaction]
      txResponses ← Future.sequence(ringTxs.map { tx =>
        val txData = getSignedTxData(
          tx.input,
          tx.nonce.intValue(),
          tx.gas,
          gasPriceRes,
          to = protocolAddress
        )
        (ethereumAccessActor ? XSendRawTransactionReq(txData))
          .mapTo[XSendRawTransactionRes]
      })
    } yield {
      // Todo(yadong) 更新新提交的TX信息
      txResponses
    }
}
