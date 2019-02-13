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

class AccountManagerAltImplSpec_QueryBalanceAllowance
    extends AccountManagerAltImplSpec {
  // import OrderStatus._

  "query balance/allowance" should "get data from Ethereum for the first time but not later" in {
    setBalanceAllowance(owner, LRC, 100, 200)

    manager.getAccountInfo(LRC).await should be(
      AccountInfo(LRC, 100, 200, 100, 200, 0)
    )

    manager.getAccountInfo(LRC).await should be(
      AccountInfo(LRC, 100, 200, 100, 200, 0)
    )
  }

  "query balance/allowance" should "get data for mutiple tokens" in {

    val tokenMap = TOKENS.map {
      _ -> (rand.nextInt.abs, rand.nextInt.abs)
    }.toMap

    tokenMap.foreach {
      case (t, (b, a)) => setBalanceAllowance(owner, t, b, a)
    }

    val accountInfoMap = tokenMap.map {
      case (t, (b, a)) => t -> AccountInfo(t, b, a, b, a, 0)
    }
    manager.getAccountInfo(TOKENS.toSet).await should be(accountInfoMap)
  }

  "canceling all existing orders" should "relase all resources" in {
    val balance = BigInt("100000000000000000")
    val allowance = BigInt("200000000000000000")

    TOKENS.foreach { t =>
      setBalanceAllowance(owner, t, balance, allowance)
    }

    val now = System.currentTimeMillis
    val num = 5000
    (1 to num) foreach { _ =>
      submitRandomOrder(Int.MaxValue)
    }

    manager.cancelAllOrders().await

    val cost = (System.currentTimeMillis - now).toDouble / num
    info(
      s"submitting $num orders then cancel them all " +
        s"cost $cost millsecond per order"
    )

    TOKENS.foreach { t =>
      manager.getAccountInfo(t).await should be(
        AccountInfo(t, balance, allowance, balance, allowance, 0)
      )
    }
  }

}
