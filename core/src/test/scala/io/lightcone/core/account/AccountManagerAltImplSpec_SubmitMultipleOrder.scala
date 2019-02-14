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

class AccountManagerAltImplSpec_SubmitMultipleOrders
    extends AccountManagerAltImplSpec {
  import OrderStatus._

  // "cancelling a older order" should "scale up younger orders" in {
  //   setSpendable(owner, LRC, 2000)
  //   setSpendable(owner, WETH, 100)

  //   val order1 = submitSingleOrderExpectingSuccess {
  //     owner |> 1000.0.lrc --> 20.0.gto -- 50.0.weth
  //   } {
  //     _.copy(
  //       status = STATUS_PENDING,
  //       _reserved = Some(MatchableState(1000, 0, 50)),
  //       _actual = Some(MatchableState(1000, 20, 50))
  //     )
  //   }

  //   val order2 = submitSingleOrderExpectingSuccess {
  //     owner |> 2000.0.lrc --> 20.0.weth
  //   } {
  //     _.copy(
  //       status = STATUS_PENDING,
  //       _reserved = Some(MatchableState(1000, 0, 0)),
  //       _actual = Some(MatchableState(1000, 10, 0))
  //     )
  //   }

  //   val order3 = submitSingleOrderExpectingSuccess {
  //     owner |> 100.0.weth --> 20.0.gto
  //   } {
  //     _.copy(
  //       status = STATUS_PENDING,
  //       _reserved = Some(MatchableState(50, 0, 0)),
  //       _actual = Some(MatchableState(50, 10, 0))
  //     )
  //   }

  //   val (success, orderMap) = manager.cancelOrder(order1.id).await
  //   success should be(true)
  //   orderMap.size should be(3)

  //   orderMap(order1.id) should be {
  //     order1.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
  //   }

  //   orderMap(order2.id) should be {
  //     order2.copy(
  //       _reserved = Some(MatchableState(2000, 0, 0)),
  //       _actual = Some(MatchableState(2000, 20, 0))
  //     )
  //   }

  //   orderMap(order3.id) should be {
  //     order2.copy(
  //       _reserved = Some(MatchableState(100, 0, 0)),
  //       _actual = Some(MatchableState(100, 20, 0))
  //     )
  //   }
  // }

  "resubmitting a older order with smaller size" should "scale up younger orders" in {
    setSpendable(owner, LRC, 2000)
    setSpendable(owner, WETH, 100)

    val order1 = submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 20.0.gto -- 50.0.weth
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 50)),
        _actual = Some(MatchableState(1000, 20, 50))
      )
    }

    val order2 = submitSingleOrderExpectingSuccess {
      owner |> 2000.0.lrc --> 20.0.weth
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 0)),
        _actual = Some(MatchableState(1000, 10, 0))
      )
    }

    val order3 = submitSingleOrderExpectingSuccess {
      owner |> 100.0.weth --> 20.0.gto
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(50, 0, 0)),
        _actual = Some(MatchableState(50, 10, 0))
      )
    }

    { // resubmitting the same order
      val (success, orderMap) = manager.resubmitOrder(order1).await
      success should be(true)
      orderMap(order1.id) should be(order1)

    }

    { // resubmitting the same order in smaller size
      val order1Smaller =
        order1.copy(amountS = 500, amountB = 10, amountFee = 25)

      val (success, orderMap) = manager.resubmitOrder(order1Smaller).await
      success should be(true)
      orderMap.size should be(3)

      orderMap(order1Smaller.id) should be {
        order1Smaller.copy(
          _reserved = Some(MatchableState(500, 0, 25)),
          _actual = Some(MatchableState(500, 10, 25))
        )
      }

      orderMap(order2.id) should be {
        order2.copy(
          _reserved = Some(MatchableState(1500, 0, 0)),
          _actual = Some(MatchableState(1500, 15, 0))
        )
      }

      orderMap(order3.id) should be {
        order3.copy(
          _reserved = Some(MatchableState(75, 0, 0)),
          _actual = Some(MatchableState(75, 15, 0))
        )
      }
    }

  }

}
