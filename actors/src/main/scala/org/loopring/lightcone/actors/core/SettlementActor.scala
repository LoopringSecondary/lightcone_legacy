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

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.lib.abi.RingSubmitterABI
import org.loopring.lightcone.lib.data._
import org.loopring.lightcone.proto.actors._
import org.web3j.crypto.RawTransaction

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

object SettlementActor {
  val name = "settlement"
}

class SettlementActor(
    actors: Lookup[ActorRef],
    submitterPrivateKey: String,
      chainId: BigInt,
protocol: String,
algorithm: SignAlgorithm.Value,
lrcAddress: String)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    submitRingAbi: RingSubmitterABI
)
  extends RepeatedJobActor
  with ActorLogging {
  //防止一个tx中的订单过多，超过 gaslimit
  private val maxRingsInOneTx = 10
  private var nonce = new AtomicInteger(0)
  implicit val ringSerializer = new RingSerializerImpl(lrcAddress)
  implicit val ringSigner = new Signer(privateKey = submitterPrivateKey)

  private def ethereumAccessActor = actors.get(EthereumAccessActor.name)
  private def gasPriceActor = actors.get(GasPriceActor.name)

  override def receive: Receive = super.receive orElse LoggingReceive {
    case req: XSettleRingsReq ⇒
      val rings = generateRings(req.rings)
      rings.foreach {
        ring ⇒
          val inputData = ring.getInputData(algorithm)
          signAndSubmitTx(inputData, req.gasLimit, req.gasPrice)
      }
  }

  def signAndSubmitTx(inputData: String, gasLimit: BigInt, gasPrice: BigInt) = {
    var hasSended = false
    while (!hasSended) {
      val rawTx = getRawTransaction(inputData, nonce.get(), gasLimit, gasPrice)
      val txData = ringSigner.signTx(rawTx, chainId)
      val sendFuture = ethereumAccessActor ? XSendRawTransaction(txData)
      //todo:需要等待提交被确认才提交下一个
      nonce.getAndIncrement()
      hasSended = true
    }

  }

  //未被提交的交易需要使用新的gas和gasprice重新提交
  def resubmitTx(): Future[Unit] = for {
    gasPriceRes ← (gasPriceActor ? XGetGasPriceReq())
      .mapTo[XGetGasPriceRes]
    //todo：查询数据库等得到未能打块的交易
    ringsWithGasLimit = Seq.empty[(String, BigInt)]
    _ = ringsWithGasLimit.foreach {
      ringWithGasLimit ⇒
        signAndSubmitTx(
          ringWithGasLimit._1,
          ringWithGasLimit._2,
          gasPriceRes.gasPrice
        )
    }
  } yield Unit

  private def generateRings(rings: Seq[XOrderRing]): Seq[CRing] = {
    @tailrec
    def generateRingRec(rings: Seq[XOrderRing], res: Seq[CRing]): Seq[CRing] = {
      if (rings.isEmpty) {
        return res
      }
      val (toSubmit, remained) = rings.splitAt(maxRingsInOneTx)
      var ring = CRing(
        ringSigner.address,
        ringSigner.address,
        "",
        Seq.empty[Seq[Int]],
        Seq.empty[COrder],
        ""
      )
      val orders = rings.flatMap {
        ring ⇒
          Set(ring.getMaker.getOrder, ring.getTaker.getOrder)
      }.distinct
      val orderIndexes = rings.map {
        ring ⇒
          Seq(
            orders.indexOf(ring.getTaker.getOrder),
            orders.indexOf(ring.getMaker.getOrder)
          )
      }
      ring = ring.copy(
        orders = orders.map(convertToOrder), //todo:
        ringOrderIndex = orderIndexes
      )
      generateRingRec(remained, res :+ ring)
    }

    generateRingRec(rings, Seq.empty[CRing])
  }

  private def convertToOrder(xOrder: XOrder): COrder = {
    //todo:need to get From db
    COrder(
      owner = "0x0",
      tokenS = xOrder.tokenS,
      tokenB = xOrder.tokenB,
      amountS = xOrder.amountS,
      amountB = xOrder.amountB,
      validSince = 0,
      allOrNone = false,
      feeToken = xOrder.tokenFee,
      feeAmount = xOrder.amountFee,
      tokenReceipt = "",
      sig = "",
      dualAuthSig = "",
      hash = xOrder.id
    )
  }

  // todo: 统一放置到ethereum模块
  private def getRawTransaction(inputData: String, nonce: BigInt, gasLimit: BigInt, gasPrice: BigInt) = {
    RawTransaction.createTransaction(
      nonce.bigInteger,
      gasPrice.bigInteger,
      gasLimit.bigInteger,
      protocol,
      BigInt(0).bigInteger,
      inputData
    )
  }

}
