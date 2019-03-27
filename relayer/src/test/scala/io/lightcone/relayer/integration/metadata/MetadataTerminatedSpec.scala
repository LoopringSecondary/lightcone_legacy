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
import io.lightcone.core._
import io.lightcone.relayer.data._
import io.lightcone.relayer._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

import scala.concurrent._

class MetadataTerminatedSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with MetadataSpecHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("change a market to mode of TERMINATE") {
    scenario(
      "the actor of this market should be stopped, " +
        "and return an error with ERR_INVALID_MARKET when submitting an order or getting orderbook"
    ) {

      implicit val account = getUniqueAccount()
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
      GetOrderbook
        .Req(
          0,
          100,
          Some(dynamicMarketPair)
        )
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
      val checkActorAliveRes2 =
        Await.result(
          checkActorAlive(system, dynamicMarketPair),
          timeout.duration
        )
      checkActorAliveRes2 should be(false)

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

//      info("change this market to ACTIVE")
//      val enableMarketF = actors.get(MetadataManagerActor.name) ?
//        UpdateMarketMetadata.Req(
//          Some(
//            metadataManager
//              .getMarket(market.hashString)
//              .copy(status = MarketMetadata.Status.ACTIVE)
//          )
//        )
//
//      Await.result(terminateMarketF, timeout.duration)
//
//      actors.get(MetadataRefresher.name) ! MetadataChanged()
//      Thread.sleep(1000)
//      val f3 = actors.get(OrderbookManagerActor.name) ? getOrderBook
//      Await.result(f3, timeout.duration)
//
//      val f2 = singleRequest(SubmitOrder.Req(Some(rawOrder1)), "submit_order")
//      Await.result(f2, timeout.duration)
//
//      info("check the status of orderbook after ACTIVE")
//      val orderbookRes2 = expectOrderbookRes(
//        getOrderBook,
//        (orderbook: Orderbook) => orderbook.sells.nonEmpty
//      )
//
//      orderbookRes2 match {
//        case Some(Orderbook(lastPrice, sells, buys)) =>
//          info(s"sells:${sells}, buys:${buys}")
//          assert(sells.nonEmpty)
//
//        case _ => assert(false)
//      }
//
//      info("then change this market to READONLY")
//      val readonlyMarketF = actors.get(MetadataManagerActor.name) ? UpdateMarketMetadata
//        .Req(
//          Some(
//            metadataManager
//              .getMarket(market.hashString)
//              .copy(status = MarketMetadata.Status.READONLY)
//          )
//        )
//      Await.result(terminateMarketF, timeout.duration)
//      actors.get(MetadataRefresher.name) ! MetadataChanged()
//
//      Thread.sleep(1000)
//      info("make sure that can't submit order in mode of READONLY")
//      val rawOrder4 =
//        createRawOrder(amountS = amountS.zeros(18), amountB = amountB.zeros(18))
//      val f4 = singleRequest(SubmitOrder.Req(Some(rawOrder4)), "submit_order")
//      try {
//        Await.result(f4, timeout.duration)
//      } catch {
//        case e: Exception =>
//          info(s"can't be submitted, res: ${e.getMessage}")
//      }
//
//      info("can get response when getOrderbook in mode of READONLY")
//      val orderbookRes4 = expectOrderbookRes(
//        getOrderBook,
//        (orderbook: Orderbook) => orderbook.sells.nonEmpty
//      )
//
//      orderbookRes4 match {
//        case Some(Orderbook(lastPrice, sells, buys)) =>
//          info(s"sells:${sells}, buys:${buys}")
//          assert(sells.nonEmpty)
//        case _ => assert(false)
//      }
    }
  }
}
