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

class AccountManagerImplSpec_RequestAmount extends OrderAwareSpec {

  "AccountManagerImpl" should "calulate request amount correctly (legacy code)" in {
    lrc.setBalanceAndAllowance(100, 200)
    gto.setBalanceAndAllowance(100, 200)
    weth.setBalanceAndAllowance(100, 200)

    var order = newOrder(
      LRC,
      WETH,
      GTO,
      50,
      20,
      50
    )
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况2: tokenFee only, balance/allowance不足
    order = newOrder(
      LRC,
      WETH,
      GTO,
      50,
      20,
      110
    )
    submitOrder(order) should be(false)

    // 情况3: tokenFee == tokenS, balance/allowance充足
    order = newOrder(
      LRC,
      WETH,
      LRC,
      30,
      10,
      10
    )
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况4: tokenFee == tokenS, balance/allowance不足
    order = newOrder(
      LRC,
      WETH,
      LRC,
      100,
      10,
      10
    )
    submitOrder(order) should be(false)

    // 情况5: tokenFee == tokenB, balance/allowance充足
    order = newOrder(
      LRC,
      WETH,
      WETH,
      30,
      20,
      120
    )
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况6: tokenFee == tokenB, balance/allowance不足
    order = newOrder(
      LRC,
      WETH,
      WETH,
      30,
      20,
      200
    )
    submitOrder(order) should be(false)
  }

}
