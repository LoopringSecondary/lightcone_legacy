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

final class AccountManagerAltImpl(
    val owner: String
  )(
    implicit
    processor: UpdatedOrdersProcessor,
    provider: BalanceAndAllowanceProvider,
    ec: ExecutionContext,
    orderPool: AccountOrderPool with UpdatedOrdersTracing)
    extends AccountManagerAlt
    with Logging {

  import OrderStatus._

  type ReserveManagerMethod = ReserveManagerAlt => Set[String]
  private implicit var tokens = Map.empty[String, ReserveManagerAlt]

  def getAccountInfo(token: String): Future[AccountInfo] =
    getReserveManagerOption(token, true).map(_.get.getAccountInfo)

  def setBalanceAndAllowance(
      token: String,
      balance: BigInt,
      allowance: BigInt
    ) =
    setBalanceAndAllowanceInternal(token) {
      _.setBalanceAndAllowance(balance, allowance)
    }

  def setBalance(
      token: String,
      balance: BigInt
    ) = setBalanceAndAllowanceInternal(token) {
    _.setBalance(balance)
  }

  def setAllowance(
      token: String,
      allowance: BigInt
    ) = setBalanceAndAllowanceInternal(token) {
    _.setAllowance(allowance)
  }

  def resubmitOrder(order: Matchable) = {
    val order_ = order.copy(_reserved = None, _actual = None, _matchable = None)
    orderPool += order_.as(STATUS_NEW) // potentially replace the old one.
    for {
      orderIdsToDelete <- reserveForOrder(order_)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ = ordersToDelete.map { order =>
        orderPool +=
          orderPool(order.id).copy(status = STATUS_SOFT_CANCELLED_LOW_BALANCE)
      }
      successful = !orderIdsToDelete.contains(order.id)
      _ = if (successful) {
        orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
      }
      updatedOrders = orderPool.takeUpdatedOrders
      _ <- processor.processOrders(updatedOrders)
    } yield (successful, updatedOrders)
  }

  def cancelOrder(orderId: String) =
    for {
      orders <- cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER)(
        orderPool.getOrder(orderId).toSeq
      )
    } yield (orders.size > 0, orders)

  def cancelOrders(orderIds: Seq[String]) =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER) {
      orderPool.orders.filter(o => orderIds.contains(o.id))
    }

  def cancelOrders(marketPair: MarketPair) =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER) {
      orderPool.orders.filter { order =>
        (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
        (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
      }
    }

  def cancelAllOrders() =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_USER)(orderPool.orders)

  def hardCancelOrder(orderId: String) =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER) {
      orderPool.getOrder(orderId).toSeq
    }

  def purgeOrders(marketPair: MarketPair) =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET, true) {
      orderPool.orders.filter { order =>
        (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
        (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
      }
    }

  def handleCutoff(cutoff: Long) =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER) {
      orderPool.orders.filter(_.validSince <= cutoff)
    }

  def handleCutoff(
      cutoff: Long,
      marketHash: String
    ) = cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER) {
    orderPool.orders.filter { order =>
      order.validSince <= cutoff &&
      MarketHash(MarketPair(order.tokenS, order.tokenB)).toString == marketHash
    }
  }

  implicit private val reserveEventHandler = new ReserveEventHandler {

    def onTokenReservedForOrder(
        orderId: String,
        token: String,
        amount: BigInt
      ) = {
      val order = orderPool(orderId)
      orderPool += order.withReservedAmount(amount)(token)
    }
  }

  private def getReserveManagerOption(
      token: String,
      mustReturn: Boolean
    ): Future[Option[ReserveManagerAlt]] =
    this.synchronized {
      if (tokens.contains(token)) Future.successful(Some(tokens(token)))
      else if (!mustReturn) Future.successful(None)
      else {
        provider.getBalanceAndALlowance(owner, token).map { result =>
          val (balance, allowance) = result
          val manager = new ReserveManagerAltClassicImpl(token)
          manager.setBalanceAndAllowance(balance, allowance)
          tokens += token -> manager
          Some(manager)
        }
      }
    }

  private def setBalanceAndAllowanceInternal(
      token: String
    )(method: ReserveManagerAlt => Set[String]
    ): Future[Map[String, Matchable]] =
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      orderIdsToDelete = method(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ <- cancelOrderInternal(STATUS_SOFT_CANCELLED_LOW_BALANCE)(
        ordersToDelete
      )
      updatedOrders = orderPool.takeUpdatedOrders
      _ <- processor.processOrders(updatedOrders)
    } yield updatedOrders

  private def cancelOrderInternal(
      status: OrderStatus,
      skipProcessingUpdatedOrders: Boolean = false
    )(orders: Iterable[Matchable]
    ) =
    for {
      _ <- Future.sequence(orders.map { order =>
        orderPool += order.copy(status = status)
        onToken(order.tokenS, _.release(order.id))
      })
      updatedOrders = orderPool.takeUpdatedOrders
      _ <- {
        if (skipProcessingUpdatedOrders) Future.unit
        else processor.processOrders(updatedOrders)
      }
    } yield updatedOrders

  private def reserveForOrder(order: Matchable): Future[Set[String]] = {
    val requestedAmountS = order.requestedAmount(order.tokenS)
    val requestedAmountFee = order.requestedAmount(order.tokenFee)

    if (requestedAmountS <= 0 || requestedAmountFee < 0) {
      orderPool += order.copy(status = STATUS_INVALID_DATA)
      Future.successful(Set(order.id))
    } else
      for {
        r1 <- onToken(order.tokenS, _.reserve(order.id, requestedAmountS))
        r2 <- {
          if (order.tokenFee == order.tokenS || requestedAmountFee == 0)
            Future.successful(Set.empty[String])
          else
            onToken(order.tokenFee, _.reserve(order.id, requestedAmountFee))
        }
        orderIdsToDelete = r1 ++ r2
      } yield orderIdsToDelete
  }

  private def onToken(
      token: String,
      invoke: ReserveManagerMethod
    ): Future[Set[String]] =
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      orderIdsToDelete = invoke(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ <- Future.sequence {
        ordersToDelete.map { order =>
          Seq(order.tokenS, order.tokenFee)
            .filter(_ != token)
            .map { t =>
              getReserveManagerOption(t, false).map { managerOpt =>
                managerOpt.foreach(_.release(order.id))
              }
            }

        }.flatten
      }
    } yield orderIdsToDelete

}