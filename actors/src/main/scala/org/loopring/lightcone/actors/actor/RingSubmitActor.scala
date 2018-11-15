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

package org.loopring.lightcone.actors.actor

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.routing.Routers
import org.loopring.lightcone.proto.actors.{Order, SubmitRingReq, Ring, SendRawTransaction}
import org.loopring.lightcone.lib.{Order => LOrder, Ring => LRing, _}
import org.loopring.lightcone.proto.deployment.RingSubmitterSettings
import org.loopring.lightcone.actors.base

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, _}

object RingSubmitActor
  extends base.Deployable[RingSubmitterSettings] {
  val name = "ring_submit_actor"

  def getCommon(s: RingSubmitterSettings) =
    base.CommonSettings(None, Seq(), 1)
}

class RingSubmitActor(
    submitter: Address
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
)
  extends RepeatedJobActor
  with ActorLogging {
  //防止一个tx中的订单过多，超过 gaslimit
  val maxRingsInOneTx = 10
  var nonce = new AtomicInteger(0)
  val ringSigner = new RingSignerImpl(privateKey = "0x1") //todo:submitter，protocol，privatekey

  override def receive: Receive = super.receive orElse LoggingReceive {
    case req: SubmitRingReq ⇒
      val rings = generateLRing(req.rings)
      rings.foreach {
        ring ⇒
          val inputData = ringSigner.generateInputData(ring)
          signAndSubmitTx(inputData)
      }
  }

  def signAndSubmitTx(inputData: String) = {
    var hasSended = false
    //todo:
    while (!hasSended) {
      val txData = ringSigner.generateTxData(inputData, nonce.get());
      val sendFuture = Routers.ethereumAccessActor ? SendRawTransaction(txData)
      val res = Await.result(sendFuture, timeout.duration)
      nonce.getAndIncrement()
      hasSended = true
    }

  }

  //未被提交的交易需要使用新的gas和gasprice重新提交再次提交
  def resubmitTx(): Future[Unit] = {
    //todo：查询数据库等得到为能及时打块的交易
    val inputDataList = Seq.empty[String]
    inputDataList.foreach(signAndSubmitTx)
    Future.successful(Unit)
  }

  private def generateLRing(rings: Seq[Ring]): Seq[LRing] = {
    @tailrec
    def generateRingRec(rings: Seq[Ring], res: Seq[LRing]): Seq[LRing] = {
      if (rings.isEmpty) {
        return res
      }
      val (toSubmit, remained) = rings.splitAt(maxRingsInOneTx)
      var lRing = LRing(
        ringSigner.getSignerAddress(),
        ringSigner.getSignerAddress(),
        "",
        Seq.empty[Seq[Int]],
        Seq.empty[LOrder],
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
      lRing = lRing.copy(
        //        orders = orders.map(convertToLOrder), //todo:
        ringOrderIndex = orderIndexes
      )
      generateRingRec(remained, res :+ lRing)
    }

    generateRingRec(rings, Seq.empty[LRing])
  }

  //todo:need to get From db
  private def convertToLOrder(order: Order): LOrder = ???

}
