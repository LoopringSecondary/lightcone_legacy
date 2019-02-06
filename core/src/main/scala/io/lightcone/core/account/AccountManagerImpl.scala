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

/// import io.lightcone.relayer.data._

import org.slf4s.Logging

final private[core] class AccountManagerImpl(
    implicit
    dustEvaluator: DustOrderEvaluator,
    orderPool: AccountOrderPool with UpdatedOrdersTracing)
    extends AccountManager
    with Logging {
  import OrderStatus._

  private[core] implicit var tokens =
    Map.empty[String, AccountTokenManager]

  def hasTokenManager(token: String): Boolean = {
    tokens.contains(token)
  }

  def addTokenManager(tm: AccountTokenManager) = {
    assert(!hasTokenManager(tm.token))
    tokens += tm.token -> tm
    tm
  }

  def getTokenManager(token: String): AccountTokenManager = {
    assert(hasTokenManager(token))
    tokens(token)
  }

  def getOrUpdateTokenManager(tm: AccountTokenManager): AccountTokenManager = {
    if (!hasTokenManager(tm.token))
      tokens += tm.token -> tm
    tokens(tm.token)
  }

  def submitOrder(order: Matchable): Boolean = {
    val order_ = order.copy(_reserved = None, _actual = None, _matchable = None)

    if (order_.amountS <= 0) {
      orderPool += order_.as(STATUS_INVALID_DATA)
      return false
    }

    if (!tokens.contains(order_.tokenS) ||
        !tokens.contains(order_.tokenFee)) {
      orderPool += order_.as(STATUS_UNSUPPORTED_MARKET)
      return false
    }

    if (order_.callOnTokenS(_.hasTooManyOrders) ||
        order_.callOnTokenFee(_.hasTooManyOrders)) {
      orderPool += order_.as(STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS)
      return false
    }

    orderPool += order_.as(STATUS_NEW)

    if (order_.callOnTokenSAndTokenFee(_.reserve(order_.id))) {
      return false
    }

    orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
    return true
  }

  def cancelOrder(orderId: String): Boolean = {
    orderPool.getOrder(orderId) match {
      case None => false
      case Some(order) =>
        cancelOrderInternal(order, STATUS_SOFT_CANCELLED_BY_USER)
        true
    }
  }

  def handleCutoff(cutoff: Long): Int = {
    val orders = orderPool.orders.filter(_.validSince <= cutoff)

    orders.foreach { order =>
      cancelOrderInternal(order, STATUS_ONCHAIN_CANCELLED_BY_USER)
    }
    orders.size
  }

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ): Int = {
    val orders = orderPool.orders.filter { order =>
      order.validSince <= cutoff &&
      MarketHash(MarketPair(order.tokenS, order.tokenB)).toString == marketHash
    }

    orders.foreach { order =>
      cancelOrderInternal(order, STATUS_ONCHAIN_CANCELLED_BY_USER)
    }
    orders.size
  }

  def purgeOrders(marketPair: MarketPair): Int = {
    val orders = orderPool.orders.filter { order =>
      (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
      (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
    }

    orders.foreach { order =>
      cancelOrderInternal(order, STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET)
      // There may be many orders, so we have to clear updated orders here.
      orderPool.takeUpdatedOrders()
    }
    orders.size
  }

  // adjust order's outstanding size
  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ): Boolean = {
    orderPool.getOrder(orderId) match {
      case None => false
      case Some(order) =>
        val outstandingAmountS_ = order.amountS min outstandingAmountS
        val order_ = order.withOutstandingAmountS(outstandingAmountS_)
        orderPool += order_
        order_.callOnTokenSAndTokenFee(_.adjust(order.id))
        true
    }
  }

  //TODO: 需要实现cancelOrdersInMarket与cancelAllOrders
  //由用户由前端请求，按照address和市场取消订单
  def cancelOrdersInMarket(marketHash: String): Int = ???

  def cancelAllOrders(): Int = ???

  private def cancelOrderInternal(
      order: Matchable,
      afterCancellationStatus: OrderStatus
    ) = {
    orderPool += order.as(afterCancellationStatus)
    order.callOnTokenSAndTokenFee(_.release(order.id))
  }

  ///-------

  implicit private class MagicOrder(order: Matchable) {

    def callOnTokenS[R](method: AccountTokenManager => R) =
      method(tokens(order.tokenS))

    def callOnTokenFee[R](method: AccountTokenManager => R) =
      method(tokens(order.tokenFee))

    // 删除订单应该有以下几种情况:
    // 1.用户主动删除订单
    // 2.订单成交后变成灰尘单
    // 3.用户账户tokenS balance不足或tokenFee balance不足
    // (除了用户主动操作以外,其他的删除动作都由tokenManager引发)
    // tokenManager的release动作不能由tokenManager本身调用,
    // 只能由orderManager根据并汇总tokenS&tokenFee情况后删除,
    // 删除时tokenS&tokenFee都要删,不能只留一个
    def callOnTokenSAndTokenFee(method: AccountTokenManager => Set[String]) = {
      val ordersToDelete = callOnTokenS(method) ++ callOnTokenFee(method)
      ordersToDelete.map { orderId =>
        callOnTokenS(_.release(orderId))
        callOnTokenFee(_.release(orderId))
      }.size > 0
    }
  }

}
