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

class AccountManagerImplSpec_QueryBalanceAllowance
    extends AccountManagerImplSpec {
  val block = 10000000L

  "query balance/allowance" should "get data from Ethereum for the first time but not later" in {
    setBalanceAllowance(block, owner, LRC, 100, 200)

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, 100, 200, 100, 200, 0, block)
    )

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, 100, 200, 100, 200, 0, block)
    )
  }

  "query balance/allowance" should "get data for mutiple tokens" in {

    val tokenMap = TOKENS.map {
      _ -> (rand.nextInt.abs, rand.nextInt.abs)
    }.toMap

    tokenMap.foreach {
      case (t, (b, a)) => setBalanceAllowance(block, owner, t, b, a)
    }

    val accountInfoMap = tokenMap.map {
      case (t, (b, a)) => t -> BalanceOfToken(t, b, a, b, a, 0, block)
    }
    manager.getBalanceOfToken(TOKENS.toSet).await should be(accountInfoMap)
  }

}
