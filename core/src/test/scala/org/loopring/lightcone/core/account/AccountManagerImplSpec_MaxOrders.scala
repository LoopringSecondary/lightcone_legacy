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

class AccountManagerImplSpec_MaxOrders extends OrderAwareSpec {
  "submit order" should "fail when max orders received for tokenS" in {
    dai.setBalanceAndAllowance(dai.maxNumOrders * 10, dai.maxNumOrders * 10)

    (1 to dai.maxNumOrders) foreach { i ⇒
      val order = sellDAI(10, 1)
      submitOrder(order) should be(true)
    }

    val order = sellDAI(10, 1)
    submitOrder(order) should be(false)
    updatedOrders.size should be(1)
    orderPool.size == dai.maxNumOrders
    updatedOrders(order.id).status should be(XOrderStatus.CANCELLED_TOO_MANY_ORDERS)
  }

  "submit order" should "fail when max orders received for tokenFee" in {
    lrc.setBalanceAndAllowance(lrc.maxNumOrders * 10, lrc.maxNumOrders * 10)
    dai.setBalanceAndAllowance(dai.maxNumOrders * 10, dai.maxNumOrders * 10)
    gto.setBalanceAndAllowance(gto.maxNumOrders * 10, gto.maxNumOrders * 10)

    (1 to dai.maxNumOrders / 2) foreach { i ⇒
      val order = sellDAI(10, 1, 10)
      submitOrder(order) should be(true)
    }

    (1 to dai.maxNumOrders / 2) foreach { i ⇒
      val order = sellGTO(10, 1, 10)
      submitOrder(order) should be(true)
    }

    val order = sellLRC(1, 1)
    submitOrder(order) should be(false)
    updatedOrders.size should be(1)
    orderPool.size == lrc.maxNumOrders
    updatedOrders(order.id).status should be(XOrderStatus.CANCELLED_TOO_MANY_ORDERS)
  }
}
