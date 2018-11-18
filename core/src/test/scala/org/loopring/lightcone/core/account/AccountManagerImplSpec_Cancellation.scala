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

class AccountManagerImplSpec_Cancellation extends OrderAwareSpec {

  "cancel order" should "fail if the order does not exist" in {
    cancelOrder("bad-id") should be(false)
    updatedOrders.size should be(0)
  }

  "cancel a single order" should "just work" in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(1000, 1000)

    val order1 = sellDAI(100, 10, 40)
    submitOrder(order1) should be(true)
    orderPool.size should be(1)

    cancelOrder(order1.id) should be(true)
    updatedOrders.size should be(1)

    updatedOrders(order1.id).status should be(XOrderStatus.CANCELLED_BY_USER)
    updatedOrders(order1.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order1.id).actual should be(orderState(0, 0, 0))

  }

  "cancel the first order" should "just scale up the following orders" in {
    dai.setBalanceAndAllowance(1000, 500)
    val order1 = sellDAI(400, 40)
    val order2 = sellDAI(200, 20)
    val order3 = sellDAI(200, 20)

    submitOrder(order1)
    submitOrder(order2)
    submitOrder(order3)
    cancelOrder(order1.id)

    orderPool.size should be(2)

    updatedOrders(order2.id).reserved should be(orderState(200, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(200, 20, 0))

    updatedOrders(order3.id).reserved should be(orderState(200, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(200, 20, 0))

  }

  "cancel an order in the middle" should "scale up the following orders" in {
    dai.setBalanceAndAllowance(1000, 500)
    val order1 = sellDAI(400, 40)
    val order2 = sellDAI(200, 20)
    val order3 = sellDAI(400, 40)

    submitOrder(order1)
    submitOrder(order2)
    submitOrder(order3)
    cancelOrder(order2.id)

    updatedOrders.size should be(2)

    updatedOrders(order3.id).reserved should be(orderState(100, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(100, 10, 0))
  }

  "cancel an order" should "free both tokenS and tokenFee" in {
    dai.setBalanceAndAllowance(2000, 1000)
    lrc.setBalanceAndAllowance(200, 100)

    val order1 = sellDAI(1000, 10, 100)
    val order2 = sellDAI(500, 5)
    val order3 = sellLRC(50, 20)

    submitOrder(order1)
    submitOrder(order2)
    submitOrder(order3)
    cancelOrder(order1.id)

    updatedOrders.size should be(3)
    orderPool.size should be(2)

    updatedOrders(order2.id).reserved should be(orderState(500, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(500, 5, 0))

    updatedOrders(order3.id).reserved should be(orderState(50, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(50, 20, 0))
  }
}
