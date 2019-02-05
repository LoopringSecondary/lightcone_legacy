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

package org.loopring.lightcone.actors.event

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._
import org.loopring.lightcone.actors.base.safefuture._

import scala.concurrent.Await

class OrdersCancelledEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {

  "ethereum event extractor actor test" must {
    "correctly extract order cancelled events from ethereum blocks" in {
      info("submit the first order.")

      val submit_order = "submit_order"
      val account0 = accounts.head
      val order1 = createRawOrder()(account0)
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )
      Thread.sleep(1000)

      info("this order must be saved in db.")
      val getOrder = Await.result(
        dbModule.orderService.getOrder(order1.hash),
        timeout.duration
      )
      getOrder match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }

      info("cancel the order just submitted")
      Await.result(
        cancelOrders(Seq(order1.hash))(account0).mapAs[SendRawTransaction.Res],
        timeout.duration
      )
      Thread.sleep(2000)
      val getOrder_2 = Await.result(
        dbModule.orderService.getOrder(order1.hash),
        timeout.duration
      )
      getOrder_2.get.getState.status should be(
        OrderStatus.STATUS_ONCHAIN_CANCELLED_BY_USER
      )
    }
  }
}
