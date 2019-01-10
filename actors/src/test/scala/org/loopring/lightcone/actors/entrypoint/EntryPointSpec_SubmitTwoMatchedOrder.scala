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

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.Await
import scala.concurrent.duration._

class EntryPointSpec_SubmitTwoMatchedOrder
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with EthereumQueryMockSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  "submit two fullMatched orders" must {
    "submit a ring and affect to orderbook before blocked and executed in eth" in {
      val order1 =
        createRawOrder(
          amountS = "10".zeros(LRC_TOKEN.decimals),
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          tokenB = WETH_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), "submit_order"),
        timeout.duration
      )

      var orderbook =
        Await.result(
          singleRequest(
            GetOrderbook.Req(
              0,
              100,
              Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
            ),
            "orderbook"
          ),
          timeout.duration
        )

      orderbook should be(
        GetOrderbook
          .Res(
            Some(
              Orderbook(
                0.0,
                Seq(Orderbook.Item("10.000000", "10.00000", "1.00000")),
                Nil
              )
            )
          )
      )

      // -----------------------
      val order2 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order2)), "submit_order"),
        timeout.duration
      )

      Thread.sleep(1000)

      orderbook = Await.result(
        singleRequest(
          GetOrderbook
            .Req(0, 100, Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))),
          "orderbook"
        ),
        timeout.duration
      )

      //  println("======" + orderbook)

      orderbook should be(
        GetOrderbook
          .Res(
            Some(
              Orderbook(
                10.0,
                Seq(Orderbook.Item("10.000000", "5.00000", "0.50000")),
                Nil
              )
            )
          )
      )

      // -----------------------
      val order3 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order3)), "submit_order"),
        timeout.duration
      )

      Thread.sleep(1000)

      orderbook = Await.result(
        singleRequest(
          GetOrderbook
            .Req(0, 100, Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))),
          "orderbook"
        ),
        timeout.duration
      )

      //  println("======" + orderbook)

      orderbook should be(
        GetOrderbook
          .Res(Some(Orderbook(10.0, Nil, Nil)))
      )

      // -----------------------
      val order4 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order4)), "submit_order"),
        timeout.duration
      )

      Thread.sleep(1000)

      orderbook = Await.result(
        singleRequest(
          GetOrderbook
            .Req(0, 100, Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))),
          "orderbook"
        ),
        timeout.duration
      )

      //  println("======" + orderbook)

      orderbook should be(
        GetOrderbook
          .Res(
            Some(
              Orderbook(
                10.0,
                Nil,
                Seq(Orderbook.Item("10.000000", "5.00000", "0.50000"))
              )
            )
          )
      )

    }
  }

}
