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

import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.account._

trait OrderAwareSpec extends CommonSpec {
  var nextId = 1

  val LRC = "LRC"
  val GTO = "GTO"
  val DAI = "DAI"
  val WETH = "WETH"

  val LRC_TOKEN = XTokenMetadata(LRC, 0, 0.1, 1.0)
  val GTO_TOKEN = XTokenMetadata(GTO, 10, 0.2, 1400.0)
  val DAI_TOKEN = XTokenMetadata(DAI, 20, 0.3, 7.0)
  val WETH_TOKEN = XTokenMetadata(WETH, 23, 0.4, 0.5)

  implicit val tmm = new TokenMetadataManager()
  tmm.addToken(LRC_TOKEN)
  tmm.addToken(GTO_TOKEN)
  tmm.addToken(DAI_TOKEN)
  tmm.addToken(WETH_TOKEN)

  implicit val tve = new TokenValueEstimator
  implicit val dustEvaluator = new DustOrderEvaluator

  implicit var orderPool: AccountOrderPoolWithUpdatedOrdersTracing = _
  var orderManager: AccountManager = _
  var lrc: AccountTokenManager = _
  var gto: AccountTokenManager = _
  var dai: AccountTokenManager = _
  var weth: AccountTokenManager = _

  var updatedOrders = Map.empty[String, Order]

  override def beforeEach() {
    nextId = 1
    orderPool = new AccountOrderPoolImpl()
    updatedOrders = Map.empty[String, Order]
    orderPool.addCallback { order ⇒
      updatedOrders += order.id -> order
      // println("----UO: " + order)
      // log.debug("order: " + order)
    }
    orderManager = AccountManager.default()

    lrc = new AccountTokenManagerImpl(LRC)
    gto = new AccountTokenManagerImpl(GTO)
    dai = new AccountTokenManagerImpl(DAI)
    weth = new AccountTokenManagerImpl(WETH)

    orderManager.addTokenManager(lrc)
    orderManager.addTokenManager(gto)
    orderManager.addTokenManager(dai)
    orderManager.addTokenManager(weth)
  }

  def sellLRC(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(LRC, WETH, LRC, amountS, amountB, amountFee)

  def buyLRC(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(WETH, LRC, LRC, amountS, amountB, amountFee)

  def sellDAI(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(DAI, WETH, LRC, amountS, amountB, amountFee)

  def buyDAI(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(WETH, DAI, LRC, amountS, amountB, amountFee)

  def sellGTO(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(GTO, WETH, LRC, amountS, amountB, amountFee)

  def buyGTO(
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = newOrder(WETH, GTO, LRC, amountS, amountB, amountFee)

  def newOrder(
    tokenS: String,
    tokenB: String,
    tokenFee: String,
    amountS: BigInt,
    amountB: BigInt,
    amountFee: BigInt = 0
  ) = Order(
    getNextId(),
    tokenS,
    tokenB,
    tokenFee,
    amountS,
    amountB,
    amountFee
  )

  def orderState(
    amountS: Long,
    amountB: Long,
    amountFee: Long
  ) = OrderState(BigInt(amountS), BigInt(amountB), BigInt(amountFee))

  def submitOrder(order: Order) = {
    updatedOrders = Map.empty[String, Order]
    orderManager.submitOrder(order)
  }

  def cancelOrder(orderId: String) = {
    updatedOrders = Map.empty[String, Order]
    orderManager.cancelOrder(orderId)
  }

  def adjustOrder(orderId: String, outstandingAmountS: Long) = {
    updatedOrders = Map.empty[String, Order]
    orderManager.adjustOrder(orderId, BigInt(outstandingAmountS))
  }

  def resetUpdatedOrders() {
    updatedOrders = Map.empty[String, Order]
  }

  implicit def longToBigInt(l: Long) = BigInt(l)

  def getNextId() = {
    val id = nextId
    nextId += 1
    id.toString
  }

  implicit class RichOrder(order: Order) {
    def asPending() = order.copy(status = XOrderStatus.PENDING)
    def withActualAsOriginal() = order.copy(_actual = Some(order.original))
    def withMatchableAsActual() = order.copy(_matchable = Some(order.actual))
    def matchableAsOriginal() = order.copy(_matchable = Some(order.original))
  }
}
