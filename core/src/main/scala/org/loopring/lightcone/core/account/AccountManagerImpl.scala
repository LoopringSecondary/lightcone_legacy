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

package org.loopring.lightcone.core.account

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto._
import org.slf4s.Logging

final private[core] class AccountManagerImpl(
  )(
    implicit orderPool: AccountOrderPool with UpdatedOrdersTracing)
    extends AccountManager
    with Logging {
  import OrderStatus._

  private[core] implicit var tokens =
    Map.empty[String, AccountTokenManager]

  def hasTokenManager(token: String): Boolean = {
    tokens.contains(token)
  }

  def addTokenManager(tm: AccountTokenManager) = this.synchronized {
    assert(!hasTokenManager(tm.token))
    tokens += tm.token -> tm
    tm
  }

  def getTokenManager(token: String): AccountTokenManager = {
    assert(hasTokenManager(token))
    tokens(token)
  }

  def getOrUpdateTokenManager(
      token: String,
      tm: AccountTokenManager
    ): AccountTokenManager = this.synchronized {
    if (!hasTokenManager(token))
      tokens += tm.token -> tm
    tokens(token)
  }

  def submitOrAdjustThenGetUpdatedOrders(
      order: Matchable
    ): (Boolean, Map[String, Matchable]) = this.synchronized {
    if (this.orderPool.contains(order.id)) {
      adjustAndGetUpdatedOrders(order.id, order.outstanding.amountS)
    } else {
      submitAndGetUpdatedOrders(order)
    }
  }

  def submitAndGetUpdatedOrders(
      order: Matchable
    ): (Boolean, Map[String, Matchable]) =
    this.synchronized {
      val submitRes = this.submitOrder(order)
      (submitRes, this.orderPool.takeUpdatedOrdersAsMap())
    }

  def adjustAndGetUpdatedOrders(
      orderId: String,
      outstandingAmountS: BigInt
    ): (Boolean, Map[String, Matchable]) = this.synchronized {
    val adjustRes = adjustOrder(orderId, outstandingAmountS)
    (adjustRes, this.orderPool.takeUpdatedOrdersAsMap())
  }

  //TODO(litao): What if an order is re-submitted?
  def submitOrder(order: Matchable): Boolean = this.synchronized {
    val order_ =
      order.copy(_reserved = None, _actual = None, _matchable = None)

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
      orderPool += order_.as(STATUS_CANCELLED_TOO_MANY_ORDERS)
      return false
    }

    orderPool += order_.as(STATUS_NEW)

    if (order_.callOnTokenSAndTokenFee(_.reserve(order_.id))) {
      return false
    }

    orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
    return true
  }

  def cancelOrder(orderId: String): Boolean = this.synchronized {
    orderPool.getOrder(orderId) match {
      case None => false
      case Some(order) =>
        orderPool.getOrder(orderId) map { order =>
          orderPool += order.as(STATUS_CANCELLED_BY_USER)
        }

        order.callOnTokenSAndTokenFee(_.release(order.id))
        true
    }
  }

  // adjust order's outstanding size
  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ): Boolean = this.synchronized {
    orderPool.getOrder(orderId) match {
      case None => false
      case Some(order) =>
        val outstandingAmountS_ = order.amountS min outstandingAmountS
        orderPool += order.withOutstandingAmountS(outstandingAmountS_)
        order.callOnTokenSAndTokenFee(_.adjust(order.id))
        true
    }
  }

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
