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

class AccountManagerImplSpec_CancelOrders extends AccountManagerImplSpec {
  import OrderStatus._
  val block = 1000000L

  "cancelling non-existing orders" should "return empty result" in {
    val (success, orderMap) = manager.cancelOrder("order0").await
    success should be(false)
    orderMap.size should be(0)

    var map = manager.cancelOrders(Seq("order0", "order1")).await
    map.size should be(0)

    map = manager.cancelOrders(LRC <-> WETH).await
    map.size should be(0)

    map = manager.cancelAllOrders().await
    map.size should be(0)

    map = manager.hardCancelOrder(block, "order0").await
    map.size should be(0)
  }

  "soft cancelling one order" should "work" in {
    val amount = 1000
    setSpendable(block, owner, LRC, amount)

    val order = submitSingleOrderExpectingSuccess {
      owner |> 100.0.lrc --> 1.0.weth
    } {
      _.copy(
        block = block,
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(100, 0, 0)),
        _actual = Some(MatchableState(100, 1, 0))
      )
    }

    softCancelSingleOrderExpectingSuccess(order.id) {
      order.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
    }

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, amount, amount, amount, amount, 0, block)
    )
  }

  "hard cancelling one order with lower block number" should "work" in {
    val amount = 1000
    setSpendable(block, owner, LRC, amount)

    val order = submitSingleOrderExpectingSuccess {
      owner |> 100.0.lrc --> 1.0.weth
    } {
      _.copy(
        block = block,
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(100, 0, 0)),
        _actual = Some(MatchableState(100, 1, 0))
      )
    }

    hardCancelSingleOrderExpectingSuccess(block - 1, order.id) {
      order.copy(block = block, status = STATUS_ONCHAIN_CANCELLED_BY_USER)
    }

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, amount, amount, amount, amount, 0, block)
    )
  }

  "hard cancelling one order with higher block number" should "work" in {
    val amount = 1000
    setSpendable(block, owner, LRC, amount)

    val order = submitSingleOrderExpectingSuccess {
      owner |> 100.0.lrc --> 1.0.weth
    } {
      _.copy(
        block = block,
        status = STATUS_PENDING,
        _reserved = Some(MatchableState(100, 0, 0)),
        _actual = Some(MatchableState(100, 1, 0))
      )
    }

    hardCancelSingleOrderExpectingSuccess(block + 1, order.id) {
      order.copy(block = block + 1, status = STATUS_ONCHAIN_CANCELLED_BY_USER)
    }

    manager.getBalanceOfToken(LRC).await should be(
      BalanceOfToken(LRC, amount, amount, amount, amount, 0, block)
    )
  }

  "canceling all orders in a market" should "work" in {
    val amount = 10000000L
    setSpendable(block + 1, owner, LRC, amount)
    setSpendable(block - 1, owner, WETH, amount)

    (1 to 100) foreach { _ =>
      submitSingleOrderExpectingSuccess {
        (owner |> 10.0.lrc --> 1.0.weth)
      } {
        _.copy(
          block = block + 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(10, 0, 0)),
          _actual = Some(MatchableState(10, 1, 0))
        )
      }
    }

    (1 to 100) foreach { _ =>
      submitSingleOrderExpectingSuccess {
        (owner |> 1.0.weth --> 110.lrc)
      } {
        _.copy(
          block = block - 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(1, 0, 0)),
          _actual = Some(MatchableState(1, 110, 0))
        )
      }
    }
    numOfOrdersProcessed should be(200)

    manager.cancelOrders(MarketPair(LRC, WETH)).await.size should be(200)
    numOfOrdersProcessed should be(400)

    manager.getBalanceOfToken(LRC).await should be {
      BalanceOfToken(LRC, amount, amount, amount, amount, 0, block + 1)
    }

    manager.getBalanceOfToken(WETH).await should be {
      BalanceOfToken(WETH, amount, amount, amount, amount, 0, block - 1)
    }
  }

  "purge orders" should "not process those orders" in {
    val amount = 10000000L
    setSpendable(block - 1, owner, LRC, amount)
    setSpendable(block + 1, owner, WETH, amount)

    (1 to 100) foreach { _ =>
      submitSingleOrderExpectingSuccess {
        (owner |> 10.0.lrc --> 1.0.weth)
      } {
        _.copy(
          block = block - 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(10, 0, 0)),
          _actual = Some(MatchableState(10, 1, 0))
        )
      }
    }

    (1 to 100) foreach { _ =>
      submitSingleOrderExpectingSuccess {
        (owner |> 1.0.weth --> 110.lrc)
      } {
        _.copy(
          block = block + 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(1, 0, 0)),
          _actual = Some(MatchableState(1, 110, 0))
        )
      }
    }
    numOfOrdersProcessed should be(200)

    manager.purgeOrders(MarketPair(LRC, WETH)).await.size should be(200)
    numOfOrdersProcessed should be(200)

    manager.getBalanceOfToken(LRC).await should be {
      BalanceOfToken(LRC, amount, amount, amount, amount, 0, block - 1)
    }

    manager.getBalanceOfToken(WETH).await should be {
      BalanceOfToken(WETH, amount, amount, amount, amount, 0, block + 1)
    }
  }

  "canceling all existing orders" should "relase all resources" in {
    val balance = BigInt("100000000000000000")
    val allowance = BigInt("200000000000000000")

    TOKENS.foreach { t =>
      setBalanceAllowance(block, owner, t, balance, allowance)
    }

    val now = System.currentTimeMillis
    val num = 5000
    (1 to num) foreach { _ =>
      submitRandomOrder(Int.MaxValue)
    }
    numOfOrdersProcessed should be(num)

    manager.cancelAllOrders().await.size should be(num)
    numOfOrdersProcessed should be(num * 2)

    val cost = (System.currentTimeMillis - now).toDouble / num
    info(
      s"submitting $num orders then cancel them all " +
        s"cost $cost millsecond per order"
    )

    TOKENS.foreach { t =>
      manager.getBalanceOfToken(t).await should be(
        BalanceOfToken(t, balance, allowance, balance, allowance, 0, block)
      )
    }
  }

  "setCutoff" should "remove all older orders whose validSince field is smaller" in {
    val amount = 10000000L
    setSpendable(block, owner, LRC, amount)
    // setSpendable(block, owner, WETH, amount)

    val orders = (1 to 100).map { i =>
      (owner |> 10.0.lrc --> 1.0.weth)
        .copy(validSince = i)
    }

    orders.foreach { order =>
      submitSingleOrderExpectingSuccess(order) {
        _.copy(
          block = block,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(10, 0, 0)),
          _actual = Some(MatchableState(10, 1, 0))
        )
      }
    }

    numOfOrdersProcessed should be(100)
    val result = manager.setCutoff(block, 40, None).await
    result.size should be(40)
    result.keys.toSet should be(orders.take(40).map(_.id).toSet)

    numOfOrdersProcessed should be(140)
  }

  "setCutoff for traiding pair" should "remove all older orders in the trading pair whose validSince field is smaller" in {
    val amount = 10000000L
    setSpendable(block + 1, owner, LRC, amount)
    setSpendable(block - 1, owner, WETH, amount)

    val orders1 = (1 to 100).map { i =>
      (owner |> 10.0.lrc --> 1.0.weth)
        .copy(validSince = i)
    }

    orders1.foreach { order =>
      submitSingleOrderExpectingSuccess(order) {
        _.copy(
          block = block + 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(10, 0, 0)),
          _actual = Some(MatchableState(10, 1, 0))
        )
      }
    }

    val orders2 = (1 to 100).map { i =>
      (owner |> 10.0.weth --> 1.0.gto)
        .copy(validSince = i)

    }

    orders2.foreach { order =>
      submitSingleOrderExpectingSuccess(order) {
        _.copy(
          block = block - 1,
          status = STATUS_PENDING,
          _reserved = Some(MatchableState(10, 0, 0)),
          _actual = Some(MatchableState(10, 1, 0))
        )
      }
    }

    val marketHash = MarketHash(MarketPair(LRC, WETH)).hashString
    numOfOrdersProcessed should be(200)
    val result = manager.setCutoff(block, 40, Some(marketHash)).await
    result.size should be(40)
    result.keys.toSet should be(orders1.take(40).map(_.id).toSet)

    numOfOrdersProcessed should be(240)
  }
}
