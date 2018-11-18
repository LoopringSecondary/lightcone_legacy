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

class AccountManagerImplSpec_Initialization extends OrderAwareSpec {
  "reinitialization of tokenS balance to smaller values" should "cancel existing orders" in {
    dai.setBalanceAndAllowance(1000, 10000)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    dai.setBalanceAndAllowance(499, 10000)

    updatedOrders.size should be(6)

    updatedOrders foreach {
      case (id, order) ⇒ order.status should be(XOrderStatus.CANCELLED_LOW_BALANCE)
    }
  }

  "reinitialization of tokenFee balance to smaller values" should "cancel existing orders" in {
    dai.setBalanceAndAllowance(1000, 10000)
    lrc.setBalanceAndAllowance(100, 1000)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1, 10)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    lrc.setBalanceAndAllowance(49, 10000)

    updatedOrders.size should be(6)

    updatedOrders foreach {
      case (id, order) ⇒ order.status should be(XOrderStatus.CANCELLED_LOW_FEE_BALANCE)
    }
  }

  "reinitialization of tokenS allowance to smaller values" should "scale down existing orders" in {
    dai.setBalanceAndAllowance(1000, 1000)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    dai.setBalanceAndAllowance(1000, 500)

    updatedOrders.size should be(5)

    updatedOrders foreach {
      case (id, order) ⇒
        order.status should be(XOrderStatus.PENDING)
        order.reserved should be(orderState(0, 0, 0))
        order.actual should be(orderState(0, 0, 0))
    }
  }

  "reinitialization of tokenFee allowance to smaller values" should "scale down existing orders" in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(100, 100)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1, 10)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    lrc.setBalanceAndAllowance(100, 50)

    updatedOrders.size should be(5)

    updatedOrders foreach {
      case (id, order) ⇒
        order.status should be(XOrderStatus.PENDING)
        order.reserved should be(orderState(100, 0, 0))
        order.actual should be(orderState(0, 0, 0))
    }
  }

  "reinitialization of tokenS balance to 0" should "cancel all orders" in {
    dai.setBalanceAndAllowance(1000, 10000)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    dai.setBalanceAndAllowance(0, 10000)

    updatedOrders.size should be(10)
    orderPool.size should be(0)
  }

  "reinitialization of tokenFee balance 0" should "cancel all orders" in {
    dai.setBalanceAndAllowance(1000, 10000)
    lrc.setBalanceAndAllowance(100, 1000)

    (1 to 10) foreach { i ⇒ submitOrder(sellDAI(100, 1, 10)) }
    orderPool.size should be(10)

    resetUpdatedOrders()
    lrc.setBalanceAndAllowance(0, 10000)

    updatedOrders.size should be(10)
    orderPool.size should be(0)
  }
}
