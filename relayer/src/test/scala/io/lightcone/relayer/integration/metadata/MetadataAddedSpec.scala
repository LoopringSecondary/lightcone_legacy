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

package io.lightcone.relayer.integration

import io.lightcone.core.MarketMetadata.Status._
import io.lightcone.core._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

import scala.concurrent.{Await, Future}

class MetadataAddedSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with MetadataSpecHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("add a new market") {
    scenario("the actor of this market should be active, and can submit orders") {

      Given("an account with enough amount")
      implicit val account = getUniqueAccount()

      Given("add a new market in db.")
      val newMarketPair = MarketPair(
        dynamicBaseToken.getMetadata.address,
        WETH_TOKEN.address
      )
      Await.result(
        dbModule.marketMetadataDal
          .saveMarket(
            MarketMetadata(
              status = ACTIVE,
              baseTokenSymbol = dynamicBaseToken.getMetadata.symbol,
              quoteTokenSymbol = WETH_TOKEN.symbol,
              maxNumbersOfOrders = 500,
              priceDecimals = 6,
              orderbookAggLevels = 5,
              precisionForAmount = 5,
              precisionForTotal = 5,
              browsableInWallet = true,
              marketPair = Some(
                newMarketPair
              ),
              marketHash = newMarketPair.hashString
            )
          ),
        timeout.duration
      )
      Thread.sleep((metadataRefresherInterval * 2 + 1) * 1000) //等待同步数据库完毕

      Then(
        "active the MarketManagerActor and OrderbookManagerActor"
      )
      val getOrderbookReq = GetOrderbook.Req(0, 100, Some(newMarketPair))
      getOrderbookReq.expectUntil(
        check((res: GetOrderbook.Res) => res.orderbook.nonEmpty)
      )

      Then("submit an order")
      val order =
        createRawOrder(
          tokenS = newMarketPair.baseToken,
          tokenB = newMarketPair.quoteToken
        )
      SubmitOrder
        .Req(Some(order))
        .expect(
          check((res: SubmitOrder.Res) => res.success)
        )

      Then("check the orderbook")
      getOrderbookReq
        .expectUntil(
          orderBookItemMatcher(
            Seq(Orderbook.Item("0.100000", "10.00000", "1.00000")),
            Seq.empty
          )
        )
    }
  }
}
