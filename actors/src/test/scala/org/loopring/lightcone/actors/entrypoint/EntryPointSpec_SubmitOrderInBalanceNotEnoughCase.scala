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

package org.loopring.lightcone.actors.entrypoint

import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitOrderInBalanceNotEnoughCase
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with EthereumSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  val account = getUniqueAccountWithoutEth

  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(
          account.getAddress,
          "10".zeros(WETH_TOKEN.decimals)
        )(accounts(0)),
        transferErc20(
          account.getAddress,
          LRC_TOKEN.address,
          "30".zeros(LRC_TOKEN.decimals)
        )(accounts(0)),
        approveErc20(
          config.getString("loopring_protocol.delegate-address"),
          LRC_TOKEN.address,
          "30".zeros(LRC_TOKEN.decimals)
        )(account)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  "submit several order when the balance is not enough" must {
    "the fisrt should be submit success and the second should be failed" in {

      //下单情况
      val rawOrders = (0 until 2) map { i =>
        createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )(account)
      }

      val f1 =
        singleRequest(SubmitOrder.Req(Some(rawOrders(0))), "submit_order").recover {
          case e: Throwable =>
            info(
              s"submit the second order shouldn't be success. it will occurs err: ${e} when submit order:${rawOrders(0)}"
            )
            Future.successful(Unit)
        }

      val res1 = Await.result(f1, timeout.duration)
      val f2 =
        singleRequest(SubmitOrder.Req(Some(rawOrders(1))), "submit_order").recover {
          case e: Throwable =>
            info(
              s"submit the second order shouldn't be success. it will occurs err: ${e} when submit order:${rawOrders(1)}"
            )
            Future.successful(Unit)
        }

      Await.result(f2, timeout.duration)

      info(
        "the first order's sequenceId in db should > 0 and status should be STATUS_PENDING and STATUS_CANCELLED_LOW_BALANCE "
      )
      val assertOrderFromDbF = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              assert(order.sequenceId > 0)
              if (order.id == rawOrders(0).hash) {
                assert(order.getState.status == OrderStatus.STATUS_PENDING)
              } else {
                assert(
                  order.getState.status == OrderStatus.STATUS_CANCELLED_LOW_BALANCE
                )
              }
            case None =>
              assert(false)
          }
        }
      })

      info("the orderbook.sells.size should be 1")
      //orderbook
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.nonEmpty)
          assert(
            sells(0).price == "20.000000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }

      info("then cancel the first one, the depth should be changed to empty.")
      val cancelReq = CancelOrder.Req(
        rawOrders(0).hash,
        rawOrders(0).owner,
        OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
        Some(MarketId(rawOrders(0).tokenS, rawOrders(0).tokenB))
      )

      val cancelF = singleRequest(cancelReq, "cancel_order")
      Await.result(cancelF, timeout.duration)

      info(
        "the first order's status in db should be STATUS_SOFT_CANCELLED_BY_USER"
      )
      val assertOrderFromDbF2 = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              if (order.hash == rawOrders(0).hash) {
                assert(
                  order.getState.status == OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
                )
              } else {
                assert(
                  order.getState.status == OrderStatus.STATUS_CANCELLED_LOW_BALANCE
                )
              }
            case None =>
              assert(false)
          }
        }
      })

      info(
        "the result of orderbook should be empty after cancel the first order."
      )

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.isEmpty
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.isEmpty && buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
