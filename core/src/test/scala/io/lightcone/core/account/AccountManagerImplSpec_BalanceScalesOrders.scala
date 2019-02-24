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

class AccountManagerImplSpec_BalanceScalesOrders
    extends AccountManagerImplSpec {
  import OrderStatus._
  val block = 1000000L

  "lowering down balance or allowance" should "scale down or cancel orders" in {
    setSpendable(block + 1, owner, LRC, 1000L)
    // setSpendable(block - 1, owner, WETH, 10L)

    val orders = (1 to 10) map { _ =>
      submitSingleOrderExpectingSuccess {
        (owner |> 100.0.lrc --> 1.0.weth)
      } {
        _.copy(
          block = block + 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(100, 0, 0)),
          _actual = Some(MatchableState(100, 1, 0))
        )
      }
    }

    numOfOrdersProcessed should be(10)

    val res = manager
      .setBalanceAndAllowance(block - 3, LRC, BigInt(300), BigInt(300))
      .await

    val cancelledOrders = orders
      .drop(3)
      .map(
        _.copy(block = block - 3, status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
      )

    res.values.toSet should be(cancelledOrders.toSet)
  }

}
