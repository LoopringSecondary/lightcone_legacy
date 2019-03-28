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

import io.lightcone.core.ErrorCode.ERR_INVALID_MARKET
import io.lightcone.core.MarketMetadata.Status.{ACTIVE, READONLY}
import io.lightcone.core._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

import scala.concurrent._

class MetadataTerminatedThenActiveSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with MetadataSpecHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature(
    "change a market to mode of TERMINATED, and then change it to ACTIVITY"
  ) {
    scenario(
      "in mode of TERMINATED the actor of this market should be stopped, " +
        "and in mode of ACTIVITY the actor of this market shoud be running."
    ) {

      implicit val account = getUniqueAccount()
      val getOrderbookReq = GetOrderbook
        .Req(
          0,
          100,
          Some(dynamicMarketPair)
        )
      Given("an account with enough amount and submit an order")
      val order1 = createRawOrder(
        tokenS = dynamicMarketPair.baseToken,
        tokenB = dynamicMarketPair.quoteToken
      )

      SubmitOrder
        .Req(Some(order1))
        .expect(check((res: SubmitOrder.Res) => res.success))
      Then("this order must be saved in db.")
      val getOrderF = dbModule.orderService.getOrder(order1.hash)
      val getOrder = Await.result(getOrderF, timeout.duration)
      getOrder.nonEmpty shouldBe true
      getOrder.get.sequenceId > 0 shouldBe true

      //orderbook
      And("check the status of orderbook now.")
      getOrderbookReq
        .expectUntil(
          orderBookItemMatcher(
            Seq(Orderbook.Item("0.100000", "10.00000", "1.00000")),
            Seq.empty
          )
        )

      Then("change market status to TERMINATE in db.")
      Await.result(
        dbModule.marketMetadataDal
          .terminateMarketByKey(dynamicMarketPair.hashString),
        timeout.duration
      )
      Thread.sleep((metadataRefresherInterval + 2) * 1000) //等待同步数据库完毕

      Then(
        "the status of MarketManagerActor and OrderbookManagerActor must be stopped"
      )
      val isActorAliveRes2 =
        Await.result(
          isActorAlive(system, dynamicMarketPair),
          timeout.duration
        )
      isActorAliveRes2 should be(false)

      And("check the response of submitting an order")
      val order2 =
        createRawOrder(
          tokenS = dynamicMarketPair.baseToken,
          tokenB = dynamicMarketPair.quoteToken,
          tokenFee = dynamicMarketPair.baseToken
        )
      SubmitOrder
        .Req(Some(order2))
        .expect(
          check((err: ErrorException) => err.error.code == ERR_INVALID_MARKET)
        )

      Then("change this market to ACTIVE")
      val f = for {
        markets <- dbModule.marketMetadataDal.getMarketsByKey(
          Seq(dynamicMarketPair.hashString)
        )
        _ <- dbModule.marketMetadataDal.updateMarket(
          markets(0).copy(status = ACTIVE)
        )
      } yield Unit
      Await.result(f, timeout.duration)
      Thread.sleep((metadataRefresherInterval + 2) * 1000) //等待同步完毕

      Then("check the status of OrderbookManager and MarketManager")
      if (system.settings.config.getBoolean("market_manager.skip-recover")) {
        getOrderbookReq
          .expectUntil(
            orderBookItemMatcher(
              Seq.empty,
              Seq.empty
            )
          )
        SubmitOrder
          .Req(Some(order2))
          .expect(
            check((res: SubmitOrder.Res) => res.success)
          )

        getOrderbookReq
          .expectUntil(
            orderBookItemMatcher(
              Seq(Orderbook.Item("0.100000", "10.00000", "1.00000")),
              Seq.empty
            )
          )
      } else {
        getOrderbookReq
          .expectUntil(
            orderBookItemMatcher(
              Seq(Orderbook.Item("0.100000", "10.00000", "1.00000")),
              Seq.empty
            )
          )
        SubmitOrder
          .Req(Some(order2))
          .expect(check((res: SubmitOrder.Res) => res.success))

        getOrderbookReq
          .expectUntil(
            orderBookItemMatcher(
              Seq(Orderbook.Item("0.100000", "20.00000", "2.00000")),
              Seq.empty
            )
          )
      }
    }
  }
}
