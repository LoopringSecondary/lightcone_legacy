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

package io.lightcone.relayer.entrypoint

import akka.pattern._
import com.google.protobuf.ByteString
import io.lightcone.relayer.actors._
import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.{Await, Future}

class EntryPointSpec_SubmitOrderThenBalanceChanged
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  val account = getUniqueAccountWithoutEth

  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(account.getAddress, "10")(accounts(0)),
        transferLRC(account.getAddress, "25")(accounts(0)),
        approveLRCToDelegate("15")(account)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    val f = transferErc20(
      accounts(0).getAddress,
      LRC_TOKEN.address,
      "25".zeros(LRC_TOKEN.decimals)
    )(account)
    Await.result(f, timeout.duration)
    super.afterAll()
  }

  "submit an order when the allowance is not enough" must {
    "store it but the depth is empty until allowance is enough" in {

      //下单情况
      val rawOrders = (0 until 1) map { i =>
        createRawOrder(
          amountS = "20".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )(account)
      }

      val f1 = Future.sequence(rawOrders.map { o =>
        singleRequest(SubmitOrder.Req(Some(o)), "submit_order")
      })

      try {
        Await.result(f1, timeout.duration)
      } catch {
        case e: ErrorException =>
          assert(e.getMessage().contains("(ERR_LOW_BALANCE"))
      }

      info(
        "the first order's sequenceId in db should > 0 and status should be STATUS_PENDING"
      )
      val assertOrderFromDbF = Future.sequence(rawOrders.map { o =>
        for {
          orderOpt <- dbModule.orderService.getOrder(o.hash)
        } yield {
          orderOpt match {
            case Some(order) =>
              assert(order.sequenceId > 0)
              assert(order.getState.status == OrderStatus.STATUS_PENDING)
            case None =>
              assert(false)
          }
        }
      })

      //orderbook
      info("the depth should be empty when allowance is not enough")
      val getOrderBook = GetOrderbook.Req(
        0,
        100,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookRes = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(
            sells(0).price == "0.050000" &&
              sells(0).amount == "12.50000" &&
              sells(0).total == "0.62500"
          )
        case _ => assert(false)
      }

      info("then make allowance enough.")
      val setAllowanceF = approveErc20(
        config.getString("loopring_protocol.delegate-address"),
        LRC_TOKEN.address,
        "25".zeros(LRC_TOKEN.decimals)
      )(account)
      Await.result(setAllowanceF, timeout.duration)

      actors.get(MultiAccountManagerActor.name) ? AddressAllowanceUpdated(
        rawOrders(0).owner,
        LRC_TOKEN.address,
        ByteString.copyFrom("25".zeros(LRC_TOKEN.decimals).toByteArray)
      )

      Thread.sleep(2000)
      info("the depth should not be empty after allowance has been set.")

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(sells.size == 1)
          assert(
            sells(0).price == "0.050000" &&
              sells(0).amount == "20.00000" &&
              sells(0).total == "1.00000"
          )
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }
}
