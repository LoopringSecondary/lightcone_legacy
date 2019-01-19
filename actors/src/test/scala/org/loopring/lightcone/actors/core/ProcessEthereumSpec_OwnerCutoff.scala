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
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent.{Await, Future}

class ProcessEthereumSpec_OwnerCutoff
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderCutoffSupport
    with OrderGenerateSupport {

  "AccountMangerActor should reject orders after receive cutoff event" must {
    "reject all orders that ValidSince <= cutoff after receive OwnerCutoff event" in {
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      info("make sure accountManagerActor can receive orders")

      val order1 = createRawOrder()(accounts(0))

      val f = singleRequest(SubmitOrder.Req(Some(order1)), "submit_order")
      Await.result(f, timeout.duration)

      val res = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      assert(res.get.sells.nonEmpty)

      info(
        "send a cutoff event to OrderCutoffHandlerActor and MultiAccountManagerActor"
      )
      val cutoff = CutoffEvent(
        header = Some(
          EventHeader(txHash = "0x1111", txStatus = TxStatus.TX_STATUS_SUCCESS)
        ),
        broker = accounts(0).getAddress,
        owner = accounts(0).getAddress,
        cutoff = timeProvider.getTimeSeconds().toInt + 100
      )
      val sendCutoffF = Future.sequence(
        Seq(
          actors.get(OrderCutoffHandlerActor.name) ? cutoff,
          actors.get(MultiAccountManagerActor.name) ? cutoff
        )
      )
      info("orderbook should empty after process cutoff")
      val res1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty
      )
      assert(res1.get.sells.isEmpty)

      info("confirm it still can receive orders that ValidSince > cutoff")
      val order2 = createRawOrder(
        validSince = timeProvider.getTimeSeconds().toInt + 1000
      )(accounts(0))
      val f2 = singleRequest(SubmitOrder.Req(Some(order2)), "submit_order")
      Await.result(f2, timeout.duration)
      val getOrderF = dbModule.orderService.getOrder(order2.hash)
      val getOrder = Await.result(getOrderF, timeout.duration)
      getOrder match {
        case Some(order) =>
          assert(
            order.sequenceId > 0 && order.getState.status == OrderStatus.STATUS_PENDING_ACTIVE
          )
        case None => assert(false)
      }

      info(
        "it will receive an JsonRpcErr with ERR_ORDER_VALIDATION_INVALID_CUTOFF when submit orders that ValidSince <= cutoff"
      )
      val order3 = createRawOrder(
        validSince = timeProvider.getTimeSeconds().toInt - 100
      )(accounts(0))
      try {
        val f3 = singleRequest(SubmitOrder.Req(Some(order3)), "submit_order")
        Await.result(f3, timeout.duration)
      } catch {
        case e: ErrorException =>
          info(e.getMessage())
          assert(e.getMessage().contains("ERR_ORDER_VALIDATION_INVALID_CUTOFF"))

      }
      val res3 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty
      )
      assert(res3.get.sells.isEmpty)
    }
  }

}
