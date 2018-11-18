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

class AccountManagerImplSpec_Performance extends OrderAwareSpec {
  "submit order" should "fail when tokenS balance is low" in {

    val _lrc = lrc.asInstanceOf[AccountTokenManagerImpl]
    lrc.setBalanceAndAllowance(100000, 100000)

    (1 to lrc.maxNumOrders) foreach { i ⇒
      val order = sellLRC(1, 1, 1)
      submitOrder(order)
    }

    val now = System.currentTimeMillis
    val count: Int = 100
    (1 to count) foreach { i ⇒
      cancelOrder(_lrc.reservations.head.orderId)
      val order = sellLRC(1, 1, 1)
      submitOrder(order)
    }

    val duration = System.currentTimeMillis - now

    val throughput = count * 1000 / duration
    println("====throughput: " + throughput) //122
  }
}
