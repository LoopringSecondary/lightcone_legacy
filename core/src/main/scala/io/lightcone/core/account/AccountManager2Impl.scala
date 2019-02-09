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

import org.slf4s.Logging
import scala.concurrent._

trait BalanceAndAllowanceProvider {

  def getBalanceAndALlowance(
      address: String,
      token: String
    ): Future[(BigInt, BigInt)]
}

final class AccountManager2Impl(
    val address: String
  )(
    implicit
    provider: BalanceAndAllowanceProvider,
    ec: ExecutionContext,
    orderPool: AccountOrderPool with UpdatedOrdersTracing)
    extends AccountManager2
    with Logging {

  import OrderStatus._

  type ReserveManagerMethod = ReserveManager2 => Set[String]
  private implicit var tokens = Map.empty[String, ReserveManager2]

  def setBalanceAndAllowance(
      token: String,
      balance: BigInt,
      allowance: BigInt
    ): Future[Int] =
    for {
      manager <- getReserveManager(token)
      orderIdsToDelete = manager.setBalanceAndAllowance(balance, allowance)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      size <- cancelOrderInternal(STATUS_SOFT_CANCELLED_LOW_BALANCE)(
        ordersToDelete
      )
    } yield size

  def getAccountInfo(token: String): Future[AccountInfo] =
    getReserveManager(token).map(_.getAccountInfo)

  def resubmitOrder(order: Matchable): Future[Boolean] = {
    val order_ = order.copy(_reserved = None, _actual = None, _matchable = None)
    orderPool += order_.as(STATUS_NEW)
    for {
      orderIdsToDelete <- order_.callOnTokenSAndTokenFee(
        m => m.reserve(order_.id, order.requestedAmount(m.token))
      )
      result = !orderIdsToDelete.contains(order_.id)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ = ordersToDelete.map { order =>
        orderPool +=
          orderPool(order.id).copy(status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
      }
      _ = if (result) {
        orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
      }
    } yield result
  }

  def hardCancelOrder(orderId: String): Future[Boolean] =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER)(
      orderPool.getOrder(orderId).toSeq
    ).map(_ > 0)

  def cancelOrder(orderId: String): Future[Boolean] =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER)(
      orderPool.getOrder(orderId).toSeq
    ).map(_ > 0)

  def cancelOrders(orderIds: Seq[String]): Future[Int] =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER)(
      orderPool.orders.filter(o => orderIds.contains(o.id))
    )

  def cancelOrders(marketPair: MarketPair): Future[Int] =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER) {
      orderPool.orders.filter { order =>
        (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
        (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
      }
    }

  def cancelAllOrders(): Future[Int] =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER)(orderPool.orders)

  def purgeOrders(marketPair: MarketPair): Future[Int] =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET) {
      orderPool.orders.filter { order =>
        (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
        (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
      }
    }

  def handleCutoff(cutoff: Long): Future[Int] =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER) {
      orderPool.orders.filter(_.validSince <= cutoff)
    }

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ): Future[Int] = cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER) {
    orderPool.orders.filter { order =>
      order.validSince <= cutoff &&
      MarketHash(MarketPair(order.tokenS, order.tokenB)).toString == marketHash
    }
  }

  private def getReserveManager(token: String): Future[ReserveManager2] =
    this.synchronized {
      if (tokens.contains(token)) Future.successful(tokens(token))
      else
        provider.getBalanceAndALlowance(address, token).map { result =>
          val (balance, allowance) = result
          val manager = new ReserveManager2Impl()(token)
          manager.setBalanceAndAllowance(balance, allowance)
          tokens += token -> manager
          manager
        }
    }

  private def cancelOrderInternal(
      status: OrderStatus
    )(orders: Iterable[Matchable]
    ): Future[Int] =
    for {
      _ <- Future.sequence(orders.map { order =>
        orderPool += order.copy(status = status)
        callOnToken(order.tokenS, _.release(order.id))
      })
      result = orders.size
    } yield result

  private def callOnToken(
      token: String,
      invoke: ReserveManagerMethod
    ): Future[Set[String]] =
    for {
      manager <- getReserveManager(token)
      orderIdsToDelete = invoke(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ <- Future.sequence(ordersToDelete.map { order =>
        val f1 =
          if (order.tokenS == token) Future.unit
          else getReserveManager(order.tokenS).map(_.release(order.id))

        val f2 =
          if (order.tokenFee == token) Future.unit
          else getReserveManager(order.tokenFee).map(_.release(order.id))
        Seq(f1, f2)
      }.flatten)
    } yield orderIdsToDelete

  implicit private class MagicOrder(order: Matchable) {

    def callOnTokenSAndTokenFee(
        invoke: ReserveManagerMethod
      ): Future[Set[String]] =
      for {
        r1 <- callOnToken(order.tokenS, invoke)
        r2 <- callOnToken(order.tokenFee, invoke)
        orderIdsToDelete = r1 ++ r2
      } yield orderIdsToDelete
  }
}
