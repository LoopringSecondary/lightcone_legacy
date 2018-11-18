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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core._
import XOrderStatus._
import XMatchingFailure._

class MarketManagerImplSpec_CalcMatchable extends MarketAwareSpec {

  "MarketManager" should "generate a ring for buy order as taker" in {
    var sellOrder = actualNotDust(sellGTO(100000, 101)) // price =  100000/101.0 = 989.12
    var buyOrder = actualNotDust(buyGTO(100000, 100)) // price =  100000/100.0 = 1000.00

    (fakePendingRingPool.getOrderPendingAmountS _).when(sellOrder.id).returns(555)
    (fakePendingRingPool.getOrderPendingAmountS _).when(buyOrder.id).returns(666)
    (fakeAggregator.getXOrderbookUpdate _).when(0).returns(XOrderbookUpdate())

    val ring = OrderRing(null, null)
    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .when(*, *, *)
      .returns(Right(ring))

    marketManager.submitOrder(sellOrder, 1)
    marketManager.submitOrder(buyOrder, 2)

    (fackRingMatcher.matchOrders(_: Order, _: Order, _: Double))
      .verify(
        buyOrder.asPending.withActualAsOriginal.copy(_matchable = Some(
          OrderState(99334, 99, 0)
        )),
        sellOrder.asPending.withActualAsOriginal.copy(_matchable = Some(
          OrderState(99445, 100, 0)
        )),
        2.0
      )
      .once
  }

}
