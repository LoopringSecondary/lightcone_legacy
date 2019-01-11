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

import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, Future}

class RingSettlementSpec
    extends CommonSpec
    with EthereumSupport
    with MarketManagerSupport
    with MultiAccountManagerSupport
    with OrderGenerateSupport
    with OrderHandleSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  def orderHandler = actors.get(OrderPersistenceActor.name)

  val account1 = getUniqueAccountWithoutEth
  //设置余额
  info("set the balance and allowance is enough befor submit an order")
  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(
          account1.getAddress,
          "10".zeros(WETH_TOKEN.decimals)
        )(accounts(0)),
        transferErc20(
          account1.getAddress,
          LRC_TOKEN.address,
          "30".zeros(LRC_TOKEN.decimals)
        )(accounts(0)),
        approveErc20(
          config.getString("loopring_protocol.delegate-address"),
          LRC_TOKEN.address,
          "30".zeros(LRC_TOKEN.decimals)
        )(account1)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  "Submit a ring tx " must {
    "tx successfully, order, balance, allowance must be right" in {

      val account0 = accounts(0)

      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val order1 = createRawOrder()(account1)

      val order2 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = order1.amountS,
        amountS = order1.amountB
      )(account0)

      val submitOrder1F =
        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
          .mapAs[SubmitOrder.Res]
      Await.result(submitOrder1F, timeout.duration)

      val orderbookRes1 = expectOrderbookRes(
        getOrderBook1,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      orderbookRes1 match {
        case Some(Orderbook(lastPrice, sells, buys)) =>
          info(s"sells:${sells}, buys:${buys}")
          assert(buys.isEmpty)
          assert(sells.size == 1)
          val head = sells.head
          assert(head.amount.toDouble == "10".toDouble)
          assert(head.price.toDouble == "10".toDouble)
          assert(head.total.toDouble == "1".toDouble)
        case _ => assert(false)
      }

      val submitOrder2F =
        singleRequest(SubmitOrder.Req(Some(order2)), submit_order)
          .mapAs[SubmitOrder.Res]
      Await.result(submitOrder2F, timeout.duration)

      val orderbookRes2 = expectOrderbookRes(
        getOrderBook1,
        (orderbook: Orderbook) => orderbook.sells.nonEmpty
      )
      //todo(yadong): 该处判断应该是什么
      info(s"${orderbookRes2}")

      Thread.sleep(500) //必须等待才能获取正确的余额，？？？
      info("the weth balance of account0 must be changed.")
      val resOpt = expectBalanceRes(
        GetBalanceAndAllowances.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetBalanceAndAllowances.Res) => {
          val wethBalance: BigInt =
            res.balanceAndAllowanceMap(WETH_TOKEN.address).balance
          wethBalance > 0
        }
      )
      info(s"balance of account0 : ${resOpt.get}")
      val wethBalance: BigInt =
        resOpt.get.balanceAndAllowanceMap(WETH_TOKEN.address).balance
      assert(wethBalance == byteString2BigInt(order1.amountB))

    }
  }

}
