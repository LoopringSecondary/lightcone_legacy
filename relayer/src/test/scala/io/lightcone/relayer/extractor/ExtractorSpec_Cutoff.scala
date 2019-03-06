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

package io.lightcone.relayer.extractor

import io.lightcone.core._
import io.lightcone.ethereum._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._
import org.web3j.crypto.Credentials

import scala.concurrent.{Await, Future}

class ExtractorSpec_Cutoff
    extends CommonSpec
    with EventExtractorSupport
    with OrderGenerateSupport {

  "AccountMangerActor should reject orders after receive OwnerCutoffEvent" must {
    "reject all orders that ValidSince <= cutoff after receive OwnerCutoff event" in {
      implicit val account: Credentials = getUniqueAccountWithoutEth
      val initAmount = Future.sequence(
        Seq(
          transferEth(account.getAddress, "10")(accounts(0)),
          transferLRC(account.getAddress, "25")(accounts(0)),
          approveLRCToDelegate("25")(account)
        )
      )

      Await.result(initAmount, timeout.duration)

      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      info("make sure accountManagerActor can receive orders")

      val order1 = createRawOrder()

      val f = singleRequest(SubmitOrder.Req(Some(order1)), "submit_order")
      Await.result(f, timeout.duration)

      val res = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      assert(res.get.sells.nonEmpty)

      info(
        "submit a cutoff event to ethereum"
      )
      Await.result(
        cancelAllOrders(timeProvider.getTimeSeconds().toInt + 100),
        timeout.duration
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
      )
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
      )
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

  "AccountMangerActor should reject orders after receive OwnerTradingPairCutoff " must {
    "reject the specific market orders after receive OwnerTradingPairCutoff event" in {
      implicit val account: Credentials = getUniqueAccountWithoutEth
      val initAmount = Future.sequence(
        Seq(
          transferEth(account.getAddress, "10")(accounts(0)),
          transferLRC(account.getAddress, "300")(accounts(0)),
          transferGTO(account.getAddress, "100")(accounts(0)),
          approveLRCToDelegate("300")(account),
          approveGTOToDelegate("100")(account)
        )
      )

      Await.result(initAmount, timeout.duration)

      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      info("make sure accountManagerActor can receive orders")

      val order1 = createRawOrder()

      val f = singleRequest(SubmitOrder.Req(Some(order1)), "submit_order")
      Await.result(f, timeout.duration)

      val res = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      assert(res.get.sells.nonEmpty)

      info(
        "submit a CutoffEvent to ethereum"
      )
      Await.result(
        cancelAllOrdersByTokenPair(
          timeProvider.getTimeSeconds().toInt + 100,
          LRC_TOKEN.address,
          WETH_TOKEN.address
        ),
        timeout.duration
      )

      info("orderbook should empty after process cutoff")
      val res1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty
      )
      assert(res1.get.sells.isEmpty)

      info("make sure it has no effect to the orders of other market")

      val orderWithOtherMarket =
        createRawOrder(tokenS = GTO_TOKEN.address, tokenB = WETH_TOKEN.address)
      val f10 = singleRequest(
        SubmitOrder.Req(Some(orderWithOtherMarket)),
        "submit_order"
      )
      Await.result(f10, timeout.duration)
      val res10 = expectOrderbookRes(
        GetOrderbook
          .Req(0, 100, Some(MarketPair(GTO_TOKEN.address, WETH_TOKEN.address))),
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      assert(res10.get.sells.nonEmpty)

      info("confirm it still can receive orders that ValidSince > cutoff")
      val order2 = createRawOrder(
        validSince = timeProvider.getTimeSeconds().toInt + 1000
      )
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
        "it will receive an JsonRpcErr with ERR_ORDER_VALIDATION_INVALID_CUTOFF" +
          "  when submit orders that ValidSince <= cutoff"
      )
      val order3 = createRawOrder(
        validSince = timeProvider.getTimeSeconds().toInt - 1000
      )
      try {
        val f3 = singleRequest(SubmitOrder.Req(Some(order3)), "submit_order")
        Await.result(f3, timeout.duration)
      } catch {
        case e: ErrorException =>
          info(e.getMessage())
          assert(e.getMessage().contains("ERR_ORDER_VALIDATION_INVALID_CUTOFF"))
      }
      val res3 = expectOrderbookRes(getOrderBook, (orderbook: Orderbook) => {
        orderbook.sells.isEmpty
      })
      assert(res3.get.sells.isEmpty)
    }
  }

}
