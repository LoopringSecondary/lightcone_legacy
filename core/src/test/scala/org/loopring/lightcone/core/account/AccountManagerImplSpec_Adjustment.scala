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

class AccountManagerImplSpec_Adjustment extends OrderAwareSpec {

  "adjustment of unexist order" should "return false" in {
    adjustOrder("bad-id", 40) should be(false)
    updatedOrders.size should be(0)
  }

  "adjustment of single order upward and downward" should "just work" in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(1000, 1000)

    val order1 = sellDAI(100, 10, 40)
    submitOrder(order1) should be(true)
    orderPool.size should be(1)

    adjustOrder(order1.id, 40) should be(true)
    updatedOrders.size should be(1)

    updatedOrders(order1.id).status should be(XOrderStatus.PENDING)
    updatedOrders(order1.id).reserved should be(orderState(40, 0, 16))
    updatedOrders(order1.id).actual should be(orderState(40, 4, 16))

    adjustOrder(order1.id, 140) should be(true)
    updatedOrders.size should be(1)

    updatedOrders(order1.id).status should be(XOrderStatus.PENDING)
    updatedOrders(order1.id).reserved should be(orderState(100, 0, 40))
    updatedOrders(order1.id).actual should be(orderState(100, 10, 40))
  }

  "adjustment of the last order upward and downward" should "just work" in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(1000, 1000)

    val order1 = sellDAI(200, 20, 200)
    submitOrder(order1) should be(true)

    val order2 = sellDAI(100, 10, 40)
    submitOrder(order2) should be(true)
    orderPool.size should be(2)

    adjustOrder(order2.id, 40)
    updatedOrders.size should be(1)

    updatedOrders(order2.id).status should be(XOrderStatus.PENDING)
    updatedOrders(order2.id).reserved should be(orderState(40, 0, 16))
    updatedOrders(order2.id).actual should be(orderState(40, 4, 16))

    adjustOrder(order2.id, 140)
    updatedOrders.size should be(1)

    updatedOrders(order2.id).status should be(XOrderStatus.PENDING)
    updatedOrders(order2.id).reserved should be(orderState(100, 0, 40))
    updatedOrders(order2.id).actual should be(orderState(100, 10, 40))
  }

  "adjustment of the first order upward" should "just scale down the following orders" in {
    dai.setBalanceAndAllowance(1000, 500)
    val order1 = sellDAI(400, 40)
    val order2 = sellDAI(200, 20)
    val order3 = sellDAI(200, 20)

    submitOrder(order1)
    submitOrder(order2)
    submitOrder(order3)
    adjustOrder(order1.id, 0)

    updatedOrders(order1.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order1.id).actual should be(orderState(0, 0, 0))

    val order4 = sellDAI(100, 10)
    submitOrder(order4)

    adjustOrder(order1.id, 400)

    updatedOrders.size should be(4)
    updatedOrders(order1.id).reserved should be(orderState(400, 0, 0))
    updatedOrders(order1.id).actual should be(orderState(400, 40, 0))

    updatedOrders(order2.id).reserved should be(orderState(100, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(100, 10, 0))

    updatedOrders(order3.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order4.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order4.id).actual should be(orderState(0, 0, 0))
  }

  "adjustment of the order in the middle upward" should "just scale down the following orders" in {
    dai.setBalanceAndAllowance(1000, 500)
    val order1 = sellDAI(400, 40)
    val order2 = sellDAI(200, 20)
    val order3 = sellDAI(200, 20)

    submitOrder(order1)
    submitOrder(order2)
    submitOrder(order3)
    adjustOrder(order2.id, 0)

    updatedOrders.size should be(2)

    updatedOrders(order2.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order3.id).reserved should be(orderState(100, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(100, 10, 0))

    adjustOrder(order2.id, 50)

    orderPool(order1.id).reserved should be(orderState(400, 0, 0))
    orderPool(order1.id).actual should be(orderState(400, 40, 0))

    updatedOrders.size should be(2)

    updatedOrders(order2.id).reserved should be(orderState(50, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(50, 5, 0))

    updatedOrders(order3.id).reserved should be(orderState(50, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(50, 5, 0))
  }
}
