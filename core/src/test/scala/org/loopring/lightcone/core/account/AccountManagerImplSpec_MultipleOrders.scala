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

class AccountManagerImplSpec_MultipleOrders extends OrderAwareSpec {
  "last order" should "fail when available balance for tokenS is smaller than required" in {
    dai.setBalanceAndAllowance(999, 999)

    (1 to 9) foreach { i ⇒
      val order = sellDAI(100, 1)
      submitOrder(order) should be(true)
      orderPool.size should be(i)
      updatedOrders(order.id).status should be(XOrderStatus.PENDING)
    }

    val order = sellDAI(100, 1)
    submitOrder(order) should be(false)
    orderPool.size should be(9)
    updatedOrders(order.id).status should be(XOrderStatus.CANCELLED_LOW_BALANCE)
  }

  "last order" should "fail when available balance for tokenFee is smaller than required" in {
    dai.setBalanceAndAllowance(10000000, 10000000)
    lrc.setBalanceAndAllowance(999, 999)

    (1 to 9) foreach { i ⇒
      val order = sellDAI(100, 1, 100)
      submitOrder(order) should be(true)
      orderPool.size should be(i)
      updatedOrders(order.id).status should be(XOrderStatus.PENDING)
    }

    val order = sellDAI(100, 1, 100)
    submitOrder(order) should be(false)
    orderPool.size should be(9)
    updatedOrders(order.id).status should be(XOrderStatus.CANCELLED_LOW_FEE_BALANCE)
  }

  "delete orders" should "scale up following orders according to tokenS" in {
    dai.setBalanceAndAllowance(1500, 500)

    val order1 = sellDAI(500, 50)
    val order2 = sellDAI(400, 40)
    val order3 = sellDAI(300, 30)

    submitOrder(order1) should be(true)
    submitOrder(order2) should be(true)
    submitOrder(order3) should be(true)
    orderPool.size should be(3)

    cancelOrder(order1.id)
    orderPool.size should be(2)
    updatedOrders.size should be(3)

    updatedOrders(order1.id).status should be(XOrderStatus.CANCELLED_BY_USER)
    updatedOrders(order1.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order1.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order2.id).reserved should be(orderState(400, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(400, 40, 0))

    updatedOrders(order3.id).reserved should be(orderState(100, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(100, 10, 0))

    cancelOrder(order2.id)
    orderPool.size should be(1)
    updatedOrders.size should be(2)

    updatedOrders(order2.id).status should be(XOrderStatus.CANCELLED_BY_USER)
    updatedOrders(order2.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order3.id).reserved should be(orderState(300, 0, 0))
    updatedOrders(order3.id).actual should be(orderState(300, 30, 0))

  }

  "delete orders" should "scale up following orders according to tokenFee" in {
    dai.setBalanceAndAllowance(100000, 100000)
    lrc.setBalanceAndAllowance(150, 50)

    val order1 = sellDAI(500, 50, 50)
    val order2 = sellDAI(400, 40, 40)
    val order3 = sellDAI(300, 30, 30)

    submitOrder(order1) should be(true)
    submitOrder(order2) should be(true)
    submitOrder(order3) should be(true)
    orderPool.size should be(3)

    orderManager.cancelOrder(order1.id)
    orderPool.size should be(2)
    updatedOrders.size should be(3)

    updatedOrders(order1.id).status should be(XOrderStatus.CANCELLED_BY_USER)
    updatedOrders(order1.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order1.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order2.id).reserved should be(orderState(400, 0, 40))
    updatedOrders(order2.id).actual should be(orderState(400, 40, 40))

    updatedOrders(order3.id).reserved should be(orderState(300, 0, 10))
    updatedOrders(order3.id).actual should be(orderState(100, 10, 10))

    cancelOrder(order2.id)
    orderPool.size should be(1)
    updatedOrders.size should be(2)

    updatedOrders(order2.id).status should be(XOrderStatus.CANCELLED_BY_USER)
    updatedOrders(order2.id).reserved should be(orderState(0, 0, 0))
    updatedOrders(order2.id).actual should be(orderState(0, 0, 0))

    updatedOrders(order3.id).reserved should be(orderState(300, 0, 30))
    updatedOrders(order3.id).actual should be(orderState(300, 30, 30))

  }
}
