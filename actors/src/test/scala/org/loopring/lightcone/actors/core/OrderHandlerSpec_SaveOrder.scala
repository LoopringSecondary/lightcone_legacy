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

import akka.pattern._
import akka.testkit.TestProbe
import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.support.{CommonSpec, OrderHandleSupport}
import org.loopring.lightcone.actors.validator.{
  MessageValidationActor,
  MultiAccountManagerMessageValidator
}
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.proto._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class OrderHandlerSpec_SaveOrder
    extends CommonSpec("""
                         |akka.cluster.roles=["order_handler"]
                         |""".stripMargin)
    with OrderHandleSupport {

  val multiAccountManagerProbe =
    new TestProbe(system, MultiAccountManagerActor.name) {

      def expectQuery() = expectMsgPF(120 second) {
        case req @ XCancelOrderReq(_, orderId, _, _) =>
          log.info(s"##### expectQuery ${req}， ${sender()}")
          sender ! XCancelOrderRes(id = orderId)
        case req: XSubmitSimpleOrderReq =>
          log.info(s"##### expectQuery ${req}， ${sender()}")
          sender ! XSubmitOrderRes(req.order)
      }

    }
  actors.del(MultiAccountManagerActor.name)
  actors.add(MultiAccountManagerActor.name, multiAccountManagerProbe.ref)

  actors.add(
    MultiAccountManagerMessageValidator.name,
    MessageValidationActor(
      new MultiAccountManagerMessageValidator(),
      MultiAccountManagerActor.name,
      MultiAccountManagerMessageValidator.name
    )
  )

  "submit a raworder" must {
    "be saved in db successful" in {
      val tokenS = "0x1"
      val tokenB = "0x2"
      val tokenFee = "0x3"
      val timeProvider = new SystemTimeProvider()
      val rawOrder = XRawOrder(
        owner = "0xa",
        hash = "0xa",
        version = 1,
        tokenS = tokenS,
        tokenB = tokenB,
        amountS = ByteString.copyFrom("11", "UTF-8"),
        amountB = ByteString.copyFrom("12", "UTF-8"),
        validSince = 1000,
        state = Some(
          XRawOrder.State(
            createdAt = timeProvider.getTimeMillis,
            updatedAt = timeProvider.getTimeMillis,
            status = XOrderStatus.STATUS_NEW
          )
        ),
        feeParams = Some(
          XRawOrder.FeeParams(
            tokenFee = tokenFee,
            amountFee = ByteString.copyFrom("111", "utf-8")
          )
        ),
        params = Some(
          XRawOrder.Params(
            validUntil = 2000
          )
        ),
        marketHash = MarketHashProvider.convert2Hex(tokenS, tokenB)
      )
      val submitReq = XSubmitOrderReq(Some(rawOrder))
      val f = actors.get(OrderHandlerActor.name) ? submitReq
      Future.successful(multiAccountManagerProbe.expectQuery())
      val res = Await.result(f.mapTo[XSubmitOrderRes], timeout.duration)
      info(s"return is : ${res}")

      val getOrderF = dbModule.orderService.getOrder(rawOrder.hash)
      val orderRes =
        Await.result(getOrderF.mapTo[Option[XRawOrder]], timeout.duration)
      orderRes should not be empty
    }
  }

}
