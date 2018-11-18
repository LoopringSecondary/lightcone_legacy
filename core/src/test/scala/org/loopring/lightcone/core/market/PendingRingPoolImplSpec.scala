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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.OrderAwareSpec
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.base._
import org.scalatest._
import org.web3j.utils.Numeric

class PendingRingPoolImplSpec extends OrderAwareSpec {
  implicit val timeProvider = new SystemTimeProvider()

  val pendingRingPool = new PendingRingPoolImpl()

  "testBaseOperation" should "add ring" in {
    info("添加10个不同环路")
    (0 until 10) foreach {
      i ⇒
        val makerExpectFill = ExpectedFill(
          order = Order(id = "maker-" + i, tokenS = LRC, tokenB = WETH, tokenFee = LRC, walletSplitPercentage = 0.2),
          pending = OrderState(amountS = 200, amountFee = 200),
          amountMargin = 100
        )
        val takerExpectFill = ExpectedFill(
          order = Order(id = "taker-" + i, tokenS = WETH, tokenB = LRC, tokenFee = LRC, walletSplitPercentage = 0.2),
          pending = OrderState(amountS = 100, amountFee = 100),
          amountMargin = 100
        )
        val ring = OrderRing(
          makerExpectFill.copy(
            amountMargin = 0,
            pending = OrderState(amountS = 100, amountFee = 10)
          ),
          takerExpectFill.copy(
            amountMargin = 0,
            pending = OrderState(amountS = 100, amountFee = 0)
          )
        )
        pendingRingPool.addRing(ring)
    }

    assert(pendingRingPool.ringMap.size == 10)
    assert(pendingRingPool.orderMap.size == 20)
    assert(pendingRingPool.getOrderPendingAmountS("taker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-2") == 100)

    //继续使用taker-1与maker-1时，需要金额保持不变
    info("继续添加 taker-1 与maker-1的环路") //或者可以改变，继续相加
    val makerExpectFill = ExpectedFill(
      order = Order(id = "maker-1", tokenS = LRC, tokenB = WETH, tokenFee = LRC, walletSplitPercentage = 0.2),
      pending = OrderState(amountS = 200, amountFee = 200),
      amountMargin = 100
    )
    val takerExpectFill = ExpectedFill(
      order = Order(id = "taker-1", tokenS = WETH, tokenB = LRC, tokenFee = LRC, walletSplitPercentage = 0.2),
      pending = OrderState(amountS = 100, amountFee = 100),
      amountMargin = 100
    )
    val ring = OrderRing(
      makerExpectFill.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 10)
      ),
      takerExpectFill.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 0)
      )
    )
    pendingRingPool.addRing(ring)
    assert(pendingRingPool.ringMap.size == 10)
    assert(pendingRingPool.orderMap.size == 20)
    assert(pendingRingPool.getOrderPendingAmountS("taker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-2") == 100)

    info("使用新的taker-new-1, 将maker-1完全吃掉")
    val takerExpectFillNew1 = ExpectedFill(
      order = Order(id = "taker-new-1", tokenS = WETH, tokenB = LRC, tokenFee = LRC, walletSplitPercentage = 0.2),
      pending = OrderState(amountS = 100, amountFee = 100),
      amountMargin = 100
    )
    val ring1 = OrderRing(
      makerExpectFill.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 10)
      ),
      takerExpectFillNew1.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 0)
      )
    )
    pendingRingPool.addRing(ring1)
    assert(pendingRingPool.ringMap.size == 11)
    assert(pendingRingPool.orderMap.size == 21)
    assert(pendingRingPool.getOrderPendingAmountS("taker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-1") == 200)
    assert(pendingRingPool.getOrderPendingAmountS("maker-2") == 100)

  }

  "testBaseOperation" should "remove ring " in {
    info("删除maker-1与taker-new-1的环路")
    val makerExpectFill = ExpectedFill(
      order = Order(id = "maker-1", tokenS = LRC, tokenB = WETH, tokenFee = LRC, walletSplitPercentage = 0.2),
      pending = OrderState(amountS = 200, amountFee = 200),
      amountMargin = 100
    )
    val takerExpectFillNew1 = ExpectedFill(
      order = Order(id = "taker-new-1", tokenS = WETH, tokenB = LRC, tokenFee = LRC, walletSplitPercentage = 0.2),
      pending = OrderState(amountS = 100, amountFee = 100),
      amountMargin = 100
    )
    val ring1 = OrderRing(
      takerExpectFillNew1.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 0)
      ),
      makerExpectFill.copy(
        amountMargin = 0,
        pending = OrderState(amountS = 100, amountFee = 10)
      )
    )
    pendingRingPool.deleteRing(ring1.id)
    assert(pendingRingPool.ringMap.size == 10)
    assert(pendingRingPool.orderMap.size == 20)
    assert(pendingRingPool.getOrderPendingAmountS("taker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("taker-new-1") == 0)
    assert(pendingRingPool.getOrderPendingAmountS("maker-1") == 100)
    assert(pendingRingPool.getOrderPendingAmountS("maker-2") == 100)

    info("将环路全部删除")
    pendingRingPool.deleteAllRings()
    assert(pendingRingPool.orderMap.isEmpty && pendingRingPool.ringMap.isEmpty)
    assert(pendingRingPool.getOrderPendingAmountS("taker-1") == 0)
  }
}
