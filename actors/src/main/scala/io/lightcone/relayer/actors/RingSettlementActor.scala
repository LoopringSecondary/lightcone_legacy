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

package io.lightcone.relayer.actors

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ ActorRef, Stash }
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.relayer.base._
import io.lightcone.relayer.base.safefuture._
import io.lightcone.relayer.ethereum._
import io.lightcone.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import io.lightcone.ethereum.abi._
import io.lightcone.proto.{ RingMinedEvent => PRingMinedEvent, _ }

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

// Owner: Yadong & Kongliang
class RingSettlementActor(
  implicit val config: Config,
  val ec: ExecutionContext,
  val timeProvider: TimeProvider,
  val timeout: Timeout,
  val actors: Lookup[ActorRef],
  val dbModule: DatabaseModule,
  val ringBatchGenerator: RingBatchGenerator)
  extends InitializationRetryActor
  with Stash
  with RepeatedJobActor {

  val selfConfig = config.getConfig(RingSettlementManagerActor.name)

  //防止一个tx中的订单过多，超过 gaslimit
  private val maxRingsInOneTx =
    selfConfig.getInt("max-rings-in-one-tx")

  private val resendDelay =
    selfConfig.getInt("resend-delay_in_seconds")

  implicit val ringContext: RingBatchContext =
    RingBatchContext(
      lrcAddress = selfConfig.getString("lrc-address"),
      feeRecipient = selfConfig.getString("fee-recipient"),
      miner = config.getString("miner"),
      transactionOrigin =
        Address(config.getString("transaction-origin")).toString,
      minerPrivateKey = config.getString("miner-privateKey"))

  implicit val credentials: Credentials =
    Credentials.create(config.getString("transaction-origin-private-key"))

  implicit val orderValidator: RawOrderValidator = new RawOrderValidatorImpl

  val protocolAddress: String =
    config.getString("loopring_protocol.protocol-address")

  val gasLimitPerRingV2 = BigInt(
    config.getString("loopring_protocol.gas-limit-per-ring-v2"))

  val chainId: Int = config.getInt(s"${EthereumClientMonitor.name}.chain_id")

  val taskQueue = new mutable.Queue[SettleRings]()

  val repeatedJobs = Seq(
    Job(
      name = selfConfig.getString("job.name"),
      dalayInSeconds = selfConfig.getInt("job.delay-in-seconds"),
      run = () => resubmitTx(),
      initialDalayInSeconds = selfConfig.getInt("job.initial-delay-in-seconds")))
  private val nonce = new AtomicInteger(0)

  private def ethereumAccessActor = actors.get(EthereumAccessActor.name)
  private def gasPriceActor = actors.get(GasPriceActor.name)
  private def marketManagerActor = actors.get(MarketManagerActor.name)

  override def initialize() = {
    val f = (ethereumAccessActor ? GetNonce.Req(
      owner = ringContext.transactionOrigin,
      tag = "latest")).mapAs[GetNonce.Res]
      .map(_.result)
      .map { validNonce =>
        nonce.set(Numeric.toBigInt(formatHex(validNonce)).intValue())
      }
    f onComplete {
      case Success(value) =>
        becomeReady()
        self ! Notify("handle_settle_rings")
      case Failure(e) =>
        throw e
    }
    f
  }

  def ready: Receive = super.receiveRepeatdJobs orElse LoggingReceive {
    case req: SettleRings =>
      val rings: Seq[Seq[OrderRing]] = truncReq2Rings(req)
      taskQueue.enqueue(rings.map(ring => {
        SettleRings(
          gasPrice = req.gasPrice,
          gasLimit = gasLimitPerRingV2 * ring.size,
          rings = ring)
      }): _*)

    case Notify("handle_settle_rings", _) =>
      handleSettleRings()
  }

  def handleSettleRings() = {
    if (taskQueue.nonEmpty) {
      val ring: SettleRings = taskQueue.dequeue()
      for {
        rawOrders: Seq[Seq[RawOrder]] <- Future.sequence(ring.rings.map {
          xOrderRing =>
            dbModule.orderService.getOrders(
              Seq(
                xOrderRing.maker.get.order.get.id,
                xOrderRing.taker.get.order.get.id))
        })
        ringBatch = ringBatchGenerator.generateAndSignRingBatch(rawOrders)
        input = ringBatchGenerator.toSubmitableParamStr(ringBatch)
        tx = Tx(
          inputData = ringSubmitterAbi.submitRing.pack(
            SubmitRingsFunction
              .Params(data = Numeric.hexStringToByteArray(input))),
          nonce.get(),
          ring.gasLimit,
          ring.gasPrice,
          protocolAddress,
          chainId = chainId)
        rawTx = getSignedTxData(tx)
        resp <- (ethereumAccessActor ? SendRawTransaction.Req(rawTx))
          .mapAs[SendRawTransaction.Res]
      } yield {
        if (resp.error.isEmpty) {
          saveTx(tx, resp)
          nonce.getAndIncrement()
        } else {
          rawOrders.foreach(orders => {
            val header = Some(EventHeader(txStatus = TxStatus.TX_STATUS_FAILED))
            marketManagerActor ! PRingMinedEvent(
              header = header,
              fills = orders.map { order =>
                OrderFilledEvent(
                  header = header,
                  orderHash = order.hash,
                  tokenS = order.tokenS)
              })
          })
        }
        self ! Notify("handle_settle_rings")
      }
    } else {
      context.system.scheduler
        .scheduleOnce(1 seconds, self, Notify("handle_settle_rings"))
    }
  }

  def truncReq2Rings(req: SettleRings): Seq[Seq[OrderRing]] = {
    val rings = ListBuffer.empty[Seq[OrderRing]]
    while (rings.size * maxRingsInOneTx < req.rings.size) {
      val startIndex = rings.size * maxRingsInOneTx
      val endIndex = Math.min(startIndex + maxRingsInOneTx, req.rings.size)
      rings.append(req.rings.slice(startIndex, endIndex))
    }
    rings
  }

  // TODO:现在逻辑是重新提交该环路，可能增加失败概率，但是长时不打块判断失败，
  // TODO：如果发送失败事件重新使用nonce，会大大增加代码复杂
  def resubmitTx(): Future[Unit] =
    for {
      gasPriceRes <- (gasPriceActor ? GetGasPrice.Req())
        .mapAs[GetGasPrice.Res]
        .map(_.gasPrice)
      ringTxs <- dbModule.settlementTxService
        .getPendingTxs(
          GetPendingTxs.Req(
            owner = ringContext.transactionOrigin,
            timeProvider.getTimeSeconds() - resendDelay))
        .map(_.txs)
      txs = ringTxs.map(
        (tx: SettlementTx) =>
          Tx(tx.data, tx.nonce.toInt, tx.gas, gasPriceRes, to = tx.to))
      txResps <- Future.sequence(txs.map { tx =>
        val rawTx = getSignedTxData(tx)
        (ethereumAccessActor ? SendRawTransaction.Req(rawTx))
          .mapAs[SendRawTransaction.Res]
      })
    } yield {
      (txs zip txResps).filter(_._2.error.isEmpty).map {
        case (tx, res) =>
          saveTx(tx, res)
      }
    }

  def saveTx(
    tx: Tx,
    res: SendRawTransaction.Res): Future[PersistSettlementTx.Res] = {
    dbModule.settlementTxService.saveTx(
      PersistSettlementTx.Req(
        tx = Some(
          SettlementTx(
            txHash = res.result,
            from = ringContext.transactionOrigin,
            to = tx.to,
            nonce = tx.nonce,
            gas = Numeric.toHexStringWithPrefix(tx.gasLimit.bigInteger),
            gasPrice = Numeric.toHexStringWithPrefix(tx.gasPrice.bigInteger),
            data = tx.inputData,
            value = Numeric.toHexStringWithPrefix(tx.value.bigInteger)))))
  }
}
