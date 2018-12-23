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

package org.loopring.lightcone.core

import org.loopring.lightcone.lib._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.account._

trait OrderAwareSpec extends CommonSpec {
  var nextId = 1

  // These are the addresses, not symbols
  val LRC = "0x00000000002"
  val GTO = "0x00000000001"
  val DAI = "0x00000000003"
  val WETH = "0x00000000004"

  implicit val tm = new TokenManager()
    .addToken(XTokenMeta(LRC, 0, 0.1, "LRC", 1.0))
    .addToken(XTokenMeta(GTO, 10, 0.2, "GTO", 1400.0))
    .addToken(XTokenMeta(DAI, 20, 0.3, "DAI", 7.0))
    .addToken(XTokenMeta(WETH, 23, 0.4, "WETH", 0.5))

  implicit val tve = new TokenValueEstimator

  implicit val dustEvaluator = new DustOrderEvaluator

  implicit var orderPool: AccountOrderPool with UpdatedOrdersTracing = _
  var accountManager: AccountManager = _
  var lrc: AccountTokenManager = _
  var gto: AccountTokenManager = _
  var dai: AccountTokenManager = _
  var weth: AccountTokenManager = _

  var updatedOrders = Map.empty[String, Order]

  override def beforeEach() {
    nextId = 1
    orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
    updatedOrders = Map.empty[String, Order]
    orderPool.addCallback { order =>
      updatedOrders += order.id -> order
    // println("----UO: " + order)
    // log.debug("order: " + order)
    }
    accountManager = AccountManager.default()

    lrc = new AccountTokenManagerImpl(LRC)
    gto = new AccountTokenManagerImpl(GTO)
    dai = new AccountTokenManagerImpl(DAI)
    weth = new AccountTokenManagerImpl(WETH)

    accountManager.addTokenManager(lrc)
    accountManager.addTokenManager(gto)
    accountManager.addTokenManager(dai)
    accountManager.addTokenManager(weth)
  }

  def sellLRC(
      amountLRC: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(LRC, WETH, LRC, amountLRC, amountWETH, amountFee)

  def buyLRC(
      amountLRC: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, LRC, LRC, amountWETH, amountLRC, amountFee)

  def sellDAI(
      amountDAI: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(DAI, WETH, LRC, amountDAI, amountWETH, amountFee)

  def buyDAI(
      amountDAI: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, DAI, LRC, amountWETH, amountDAI, amountFee)

  def sellGTO(
      amountGTO: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(GTO, WETH, LRC, amountGTO, amountWETH, amountFee)

  def buyGTO(
      amountGTO: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, GTO, LRC, amountWETH, amountGTO, amountFee)

  def newOrder(
      tokenS: String,
      tokenB: String,
      tokenFee: String,
      amountS: BigInt,
      amountB: BigInt,
      amountFee: BigInt
    ): Order =
    Order(getNextId(), tokenS, tokenB, tokenFee, amountS, amountB, amountFee)

  def sellLRC(
      amountLRC: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(LRC, WETH, LRC, amountLRC, amountWETH, amountFee)

  def buyLRC(
      amountLRC: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, LRC, LRC, amountWETH, amountLRC, amountFee)

  def sellDAI(
      amountDAI: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(DAI, WETH, LRC, amountDAI, amountWETH, amountFee)

  def buyDAI(
      amountDAI: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, DAI, LRC, amountWETH, amountDAI, amountFee)

  def sellGTO(
      amountGTO: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(GTO, WETH, LRC, amountGTO, amountWETH, amountFee)

  def buyGTO(
      amountGTO: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, GTO, LRC, amountWETH, amountGTO, amountFee)

  def newOrder(
      tokenS: String,
      tokenB: String,
      tokenFee: String,
      amountS: Double,
      amountB: Double,
      amountFee: Double
    ): Order =
    Order(
      getNextId(),
      tokenS,
      tokenB,
      tokenFee,
      amountS.toWei(tokenS),
      amountB.toWei(tokenB),
      amountFee.toWei(tokenFee)
    )

  def orderState(
      amountS: Long,
      amountB: Long,
      amountFee: Long
    ) = OrderState(BigInt(amountS), BigInt(amountB), BigInt(amountFee))

  def submitOrder(order: Order) = {
    updatedOrders = Map.empty[String, Order]
    accountManager.submitOrder(order)
  }

  def cancelOrder(orderId: String) = {
    updatedOrders = Map.empty[String, Order]
    accountManager.cancelOrder(orderId)
  }

  def adjustOrder(
      orderId: String,
      outstandingAmountS: Long
    ) = {
    updatedOrders = Map.empty[String, Order]
    accountManager.adjustOrder(orderId, BigInt(outstandingAmountS))
  }

  def resetUpdatedOrders() {
    updatedOrders = Map.empty[String, Order]
  }

  def getNextId() = {
    val id = nextId
    nextId += 1
    id.toString
  }

}
