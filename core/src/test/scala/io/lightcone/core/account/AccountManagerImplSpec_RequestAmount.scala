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

package io.lightcone.core
import io.lightcone.core.implicits._

// import io.lightcone.core.OrderAwareSpec

/// import io.lightcone.proto._

import org.scalatest._

class AccountManagerImplSpec_RequestAmount extends OrderAwareSpec {

  "AccountManagerImpl" should "calulate request amount correctly (legacy code)" in {
    lrc.setBalanceAndAllowance(100, 200)
    gto.setBalanceAndAllowance(100, 200)
    weth.setBalanceAndAllowance(100, 200)

    var order = newOrder(LRC, WETH, GTO, BigInt(50), BigInt(20), BigInt(50))
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况2: tokenFee only, balance/allowance不足
    order = newOrder(LRC, WETH, GTO, BigInt(50), BigInt(20), BigInt(110))
    submitOrder(order) should be(false)

    // 情况3: tokenFee == tokenS, balance/allowance充足
    order = newOrder(LRC, WETH, LRC, BigInt(30), BigInt(10), BigInt(10))
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况4: tokenFee == tokenS, balance/allowance不足
    order = newOrder(LRC, WETH, LRC, BigInt(100), BigInt(10), BigInt(10))
    submitOrder(order) should be(false)

    // 情况5: tokenFee == tokenB, balance/allowance充足
    order = newOrder(LRC, WETH, WETH, BigInt(30), BigInt(20), BigInt(120))
    submitOrder(order) should be(true)
    cancelOrder(order.id)

    // 情况6: tokenFee == tokenB, balance/allowance不足
    order = newOrder(LRC, WETH, WETH, BigInt(30), BigInt(20), BigInt(200))
    submitOrder(order) should be(false)
  }

}
