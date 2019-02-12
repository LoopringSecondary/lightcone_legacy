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

package io.lightcone.relayer.event

import io.lightcone.relayer.support._
import io.lightcone.relayer.data.SubmitOrder
import io.lightcone.relayer.base._
import scala.concurrent.Await

// TODO(yadong): this test fail if you run `sbt relayer/test` but will success if you run it alone.
class CutOffEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {

  "ethereum balance update event and transfer event extractor actor test" must {
    "correctly extract balance update events and transfer events from ethereum blocks" in {

      val submit_order = "submit_order"
      val account0 = accounts.head

      val order1 = createRawOrder()(account0)
      info("submit first order ")
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )
      Thread.sleep(2000)

      info("this order must be saved in db.")
      val getOrder2 = Await.result(
        dbModule.orderService.getOrder(order1.hash),
        timeout.duration
      )
      getOrder2 match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }
      info("cancel all orders by set cutOff")
      Await.result(
        cancelAllOrders(timeProvider.getTimeSeconds())(account0),
        timeout.duration
      )
      Thread.sleep(2000)
      val getOrder2_2 = Await.result(
        dbModule.orderService.getOrder(order1.hash),
        timeout.duration
      )
      getOrder2_2.get.getState.status.isStatusOnchainCancelledByUser should be(
        true
      )

      info("submit second order")
      val order3 = createRawOrder()(account0)
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order3)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )
      Thread.sleep(2000)

      info("this order must be saved in db.")
      val getOrder3 = Await.result(
        dbModule.orderService.getOrder(order3.hash),
        timeout.duration
      )
      getOrder3 match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }

      info("cancel all orders by set cutOff and token pair")
      Await.result(
        cancelAllOrdersByTokenPair(timeProvider.getTimeSeconds())(account0),
        timeout.duration
      )
      Thread.sleep(2000)
      val getOrder3_2 = Await.result(
        dbModule.orderService.getOrder(order3.hash),
        timeout.duration
      )
      getOrder3_2.get.getState.status.isStatusOnchainCancelledByUser should be(
        true
      )
    }

  }

}
