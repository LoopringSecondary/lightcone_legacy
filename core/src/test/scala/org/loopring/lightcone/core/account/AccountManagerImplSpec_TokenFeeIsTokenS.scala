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

package org.loopring.lightcone.core.account

import org.loopring.lightcone.core.OrderAwareSpec
import org.loopring.lightcone.core.data._
import org.scalatest._

class AccountManagerImplSpec_TokenFeeIsTokenS extends OrderAwareSpec {
  "when tokenS == tokenFee, submit order" should "fail when tokenS balance is low" in {
    lrc.setBalanceAndAllowance(100, 0)
    val order = sellLRC(100, 1, 10)
    submitOrder(order) should be(false)
    orderPool.size should be(0)
    updatedOrders(order.id).status should be(XOrderStatus.CANCELLED_LOW_BALANCE)
  }

  "when tokenS == tokenFee, submit order" should "reserve for both tokenS and tokenFee when allowance is suffcient" in {
    lrc.setBalanceAndAllowance(1000, 1000)
    val order = sellLRC(100, 10, 10)
    submitOrder(order) should be(true)
    orderPool.size should be(1)
    updatedOrders(order.id).status should be(XOrderStatus.PENDING)

    updatedOrders(order.id).reserved should be(orderState(100, 0, 10))
    updatedOrders(order.id).actual should be(orderState(100, 10, 10))
  }

  "when tokenS == tokenFee, submit order" should "reserve for both tokenS and tokenFee when allowance is insuffcient" in {
    lrc.setBalanceAndAllowance(1000, 55)
    val order = sellLRC(200, 10, 20)
    submitOrder(order) should be(true)
    orderPool.size should be(1)
    updatedOrders(order.id).status should be(XOrderStatus.PENDING)

    updatedOrders(order.id).reserved should be(orderState(50, 0, 5))
    updatedOrders(order.id).actual should be(orderState(50, 2, 5))
  }
}
