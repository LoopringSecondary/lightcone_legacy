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

class AccountManagerImplSpec_RestrictedByAllowance extends OrderAwareSpec {

  "order" should "be scaled by tokenS allowance (non-zero) if tokenFee allowance is still high " in {
    dai.setBalanceAndAllowance(1000, 100)
    lrc.setBalanceAndAllowance(1000, 1000)

    val order = sellDAI(200, 1000, 20)

    submitOrder(order) should be(true)
    orderPool.size should be(1)
    val updated = orderPool(order.id)
    updatedOrders(order.id) should be(updated)

    updatedOrders(order.id).reserved should be(orderState(100, 0, 20))
    updatedOrders(order.id).actual should be(orderState(100, 500, 10))
  }

  "order" should "be scaled by tokenS allowance (zero) if tokenFee allowance is still high " in {
    dai.setBalanceAndAllowance(1000, 0)
    lrc.setBalanceAndAllowance(1000, 1000)

    val order = sellDAI(200, 1000, 20)

    submitOrder(order) should be(true)
    orderPool.size should be(1)
    val updated = orderPool(order.id)
    updatedOrders(order.id) should be(updated)

    val reserved = updated.reserved
    val actual = updated.actual

    reserved should be(orderState(0, 0, 20))
    actual should be(orderState(0, 0, 0))
  }

  "order" should "be scaled by tokenFee allowance (non-zero) if tokenS allowance is still high " in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(1000, 10)

    val order = sellDAI(200, 1000, 20)

    submitOrder(order) should be(true)
    orderPool.size should be(1)
    val updated = orderPool(order.id)
    updatedOrders(order.id) should be(updated)

    val reserved = updated.reserved
    val actual = updated.actual

    reserved should be(orderState(200, 0, 10))
    actual should be(orderState(100, 500, 10))
  }

  "order" should "be scaled by tokenFee allowance (zero) if tokenS allowance is still high " in {
    dai.setBalanceAndAllowance(1000, 1000)
    lrc.setBalanceAndAllowance(1000, 0)

    val order = sellDAI(200, 1000, 20)

    submitOrder(order) should be(true)
    orderPool.size should be(1)
    val updated = orderPool(order.id)
    updatedOrders(order.id) should be(updated)

    val reserved = updated.reserved
    val actual = updated.actual

    reserved should be(orderState(200, 0, 0))
    actual should be(orderState(0, 0, 0))
  }
}
