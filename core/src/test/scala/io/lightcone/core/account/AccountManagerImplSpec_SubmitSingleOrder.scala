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

class AccountManagerImplSpec_SubmitSingleOrder extends AccountManagerImplSpec {
  import OrderStatus._

  "If fee is 0, submitOrder" should "fail when tokenS balance/allowance is 0" in {
    setSpendable(owner, LRC, 0)

    submitSingleOrderExpectingFailure {
      owner |> 100.0.lrc --> 1.0.weth
    } {
      _.copy(status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
    }
  }

  it should "succeed when tokenS balance/allowance is suffcient" in {
    setSpendable(owner, LRC, 1000)

    val order = submitSingleOrderExpectingSuccess {
      (owner |> 100.0.lrc --> 1.0.weth).copy(
        validSince = 123L, // keep as-is
        submittedAt = 456L, // keep as-is
        numAttempts = 12, // keep as-is
        walletSplitPercentage = 0.3, // keep as-is
        _reserved = Some(MatchableState(-1, -1, -1)), // update
        _actual = Some(MatchableState(-2, -2, -2))) // update
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(100, 0, 0)),
        _actual = Some(MatchableState(100, 1, 0)))
    }

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, 1000, 1000, 900, 900, 1))

    softCancelSingleOrderExpectingSuccess(order.id) {
      order.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
    }

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, 1000, 1000, 1000, 1000, 0))

  }

  it should "succeed when tokenS balance/allowance is insuffcient but non-zero" in {
    setSpendable(owner, LRC, 250)

    val order = submitSingleOrderExpectingSuccess {
      (owner |> 1000.0.lrc --> 60.0.weth)
        .copy(_outstanding = Some(MatchableState(500, 30, 0))) // will use these values
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(250, 0, 0)),
        _actual = Some(MatchableState(250, 15, 0)))
    }

    softCancelSingleOrderExpectingSuccess(order.id) {
      order.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
    }
  }

  "If fee >0 and tokenFee != tokenS & tokenFee != tokenB, submitOrder" should
    "fail when tokenS balance/allowance is 0" in {
      setSpendable(owner, LRC, 0)
      setSpendable(owner, GTO, 100)

      submitSingleOrderExpectingFailure {
        owner |> 100.0.lrc --> 1.0.weth -- 10.gto
      } {
        _.copy(
          status = STATUS_SOFT_CANCELLED_LOW_BALANCE,
          _reserved = Some(MatchableState(0, 0, 10)),
          _actual = Some(MatchableState(0, 0, 0)))
      }
    }

  it should "fail when tokenFee balance/allowance is 0" in {
    setSpendable(owner, LRC, 1000)
    setSpendable(owner, GTO, 0)

    submitSingleOrderExpectingFailure {
      owner |> 100.0.lrc --> 1.0.weth -- 10.gto
    } {
      _.copy(
        status = STATUS_SOFT_CANCELLED_LOW_BALANCE,
        _reserved = Some(MatchableState(100, 0, 0)),
        _actual = Some(MatchableState(0, 0, 0)))
    }
  }

  it should "succeed when tokenS and tokenFee balance/allowance are both suffcient" in {

    setSpendable(owner, LRC, 1000)
    setSpendable(owner, GTO, 10)

    submitSingleOrderExpectingSuccess {
      owner |> 100.0.lrc --> 1.0.weth -- 5.gto
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(100, 0, 5)),
        _actual = Some(MatchableState(100, 1, 5)))
    }
  }
  it should "succeed when tokenS is suffcient but tokenFee is not" in {
    setSpendable(owner, LRC, 500)
    setSpendable(owner, GTO, 20)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 10.0.weth -- 20.gto
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(500, 0, 20)),
        _actual = Some(MatchableState(500, 5, 10)))
    }
  }

  it should "succeed when tokenFee is suffcient but tokenS is not" in {

    setSpendable(owner, LRC, 1000)
    setSpendable(owner, GTO, 10)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 10.0.weth -- 20.gto
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 10)),
        _actual = Some(MatchableState(500, 5, 10)))
    }
  }

  it should "succeed when tokenS and tokenFee are both insuffcient" in {
    setSpendable(owner, LRC, 400)
    setSpendable(owner, GTO, 10)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 40.0.weth -- 20.gto
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(400, 0, 10)),
        _actual = Some(MatchableState(400, 16, 8)))
    }
  }

  "If fee >0 and tokenFee == tokenS, submitOrder" should "fail when tokenS balance/allowance is 0" in {
    setSpendable(owner, LRC, 0)

    submitSingleOrderExpectingFailure {
      owner |> 100.0.lrc --> 1.0.weth -- 10.0.lrc
    } {
      _.copy(status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
    }
  }

  it should "succeed when tokenS balance/allowance is suffcient" in {
    setSpendable(owner, LRC, 2000)

    submitSingleOrderExpectingSuccess {
      owner |> 990.0.lrc --> 20.0.weth -- 10.0.lrc
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(990, 0, 10)),
        _actual = Some(MatchableState(990, 20, 10)))
    }
  }

  it should "succeed when tokenS balance/allowance is insuffcient" in {
    setSpendable(owner, LRC, 500)

    submitSingleOrderExpectingSuccess {
      owner |> 990.0.lrc --> 20.0.weth -- 10.0.lrc
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(495, 0, 5)),
        _actual = Some(MatchableState(495, 10, 5)))
    }
  }

  "If fee > 0 and tokenFee == tokenB, submitOrder" should
    "fail when tokenS balance/allowance is 0" in {
      setSpendable(owner, LRC, 0)

      submitSingleOrderExpectingFailure {
        owner |> 1000.0.lrc --> 20.0.weth -- 4.0.weth
      } {
        _.copy(status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
      }
    }

  it should "succeed when amountFee <= amountB  and not attemp to reserve the fee token" in {
    setSpendable(owner, LRC, 2000)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 20.0.weth -- 4.0.weth
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 0)),
        _actual = Some(MatchableState(1000, 20, 4)))
    }
  }

  it should "fail when amountFee > amountB and tokenFee balance/allowance is 0" in {
    setSpendable(owner, LRC, 2000)
    setSpendable(owner, WETH, 0)

    submitSingleOrderExpectingFailure {
      owner |> 1000.0.lrc --> 20.0.weth -- 40.0.weth
    } {
      _.copy(
        status = STATUS_SOFT_CANCELLED_LOW_BALANCE,
        _reserved = Some(MatchableState(1000, 0, 0)),
        _actual = Some(MatchableState(0, 0, 0)))
    }
  }

  it should "succeed when amountFee > amountB amountB and tokenFee balance/allowance insuffcient" in {
    setSpendable(owner, LRC, 2000)
    setSpendable(owner, WETH, 10)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 20.0.weth -- 40.0.weth
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 10)),
        _actual = Some(MatchableState(500, 10, 20)))
    }
  }

  it should "succeed when amountFee > amountB amountB and tokenFee balance/allowance suffcient" in {
    setSpendable(owner, LRC, 2000)
    setSpendable(owner, WETH, 100)

    submitSingleOrderExpectingSuccess {
      owner |> 1000.0.lrc --> 20.0.weth -- 40.0.weth
    } {
      _.copy(
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(1000, 0, 20)),
        _actual = Some(MatchableState(1000, 20, 40)))
    }
  }

}
