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
import io.lightcone.lib.FutureUtil._

// This class is not thread safe.
final class AccountManagerImpl(
    val owner: String,
    enableTracing: Boolean = false
  )(
    implicit
    updatedOrdersProcessor: UpdatedOrdersProcessor,
    updatedAccountsProcessor: UpdatedAccountsProcessor,
    provider: BalanceAndAllowanceProvider,
    ec: ExecutionContext)
    extends AccountManager
    with Logging {

  import OrderStatus._
  import ErrorCode._

  type ReserveManagerMethod = ReserveManager => Set[String]
  private val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  private implicit var tokens = Map.empty[String, ReserveManager]

  def getNumOfOrders() = orderPool.size

  def getBalanceOfToken(token: String): Future[BalanceOfToken] =
    getReserveManagerOption(token, true).map(_.get.getBalanceOfToken)

  def getBalanceOfToken(
      tokens_ : Set[String]
    ): Future[Map[String, BalanceOfToken]] =
    getReserveManagers(tokens_, true).map(_.map {
      case (token, manager) => token -> manager.getBalanceOfToken
    })

  def setBalanceAndAllowance(
      block: Long,
      token: String,
      balance: BigInt,
      allowance: BigInt
    ) =
    setBalanceAndAllowanceInternal(block, token) {
      _.setBalanceAndAllowance(block, balance, allowance)
    }

  def setBalance(
      block: Long,
      token: String,
      balance: BigInt
    ) = setBalanceAndAllowanceInternal(block, token) {
    _.setBalance(block, balance)
  }

  def setAllowance(
      block: Long,
      token: String,
      allowance: BigInt
    ) = setBalanceAndAllowanceInternal(block, token) {
    _.setAllowance(block, allowance)
  }

  def resubmitOrder(order: Matchable) = {
    for {
      _ <- Future.unit
      order_ = order.copy(_reserved = None, _actual = None, _matchable = None)
      _ = { orderPool += order_.as(STATUS_PENDING) } // potentially replace the old one.
      (block, orderIdsToDelete) <- reserveForOrder(order_)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ = ordersToDelete.map { order =>
        orderPool +=
          orderPool(order.id).copy(
            block = order.block.max(block),
            status = STATUS_SOFT_CANCELLED_LOW_BALANCE
          )
      }
      successful = !orderIdsToDelete.contains(order.id)
      _ = if (successful) {
        orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
      }
      updatedOrders = orderPool.takeUpdatedOrders
      _ <- updatedOrdersProcessor.processUpdatedOrders(true, updatedOrders)
    } yield (successful, updatedOrders)
  }

  def cancelOrder(
      orderId: String,
      status: OrderStatus = STATUS_SOFT_CANCELLED_BY_USER
    ) =
    for {
      orders <- cancelOrderInternal(status)(orderPool.getOrder(orderId).toSeq)
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

  def hardCancelOrder(
      block: Long,
      orderId: String
    ) =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER, Option(block)) {
      orderPool.getOrder(orderId).toSeq
    }

  def purgeOrders(marketPair: MarketPair) =
    cancelOrderInternal(STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET, None, true) {
      orderPool.orders.filter { order =>
        (order.tokenS == marketPair.quoteToken && order.tokenB == marketPair.baseToken) ||
        (order.tokenB == marketPair.quoteToken && order.tokenS == marketPair.baseToken)
      }
    }

  def handleCutoff(
      block: Long,
      cutoff: Long
    ) =
    cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER, Some(block)) {
      orderPool.orders.filter(_.validSince <= cutoff)
    }

  def handleCutoff(
      block: Long,
      cutoff: Long,
      marketHash: String
    ) = cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER, Some(block)) {
    orderPool.orders.filter { order =>
      order.validSince <= cutoff && MarketHash(
        MarketPair(order.tokenS, order.tokenB)
      ).hashString == marketHash
    }
  }

  implicit private val reserveEventHandler = new ReserveEventHandler {

    def onTokenReservedForOrder(
        block: Long,
        orderId: String,
        token: String,
        amount: BigInt
      ) = {
      val order = orderPool(orderId)
      orderPool += order
        .withReservedAmount(amount)(token)
        .copy(block = order.block.max(block))
    }
  }

  private def setBalanceAndAllowanceInternal(
      block: Long,
      token: String
    )(method: ReserveManager => Set[String]
    ): Future[Map[String, Matchable]] = {
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      lastBlock = manager.getBlock
      orderIdsToDelete = method(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      _ <- cancelOrderInternal(
        STATUS_SOFT_CANCELLED_LOW_BALANCE,
        Some(lastBlock)
      )(ordersToDelete)
      updatedOrders = orderPool.takeUpdatedOrders
      _ <- updatedOrdersProcessor.processUpdatedOrders(true, updatedOrders)

      _ <- {
        if (lastBlock == block) Future.unit
        // TODO(dongw): Should we also update lastBlock?
        else updatedAccountsProcessor.processUpdatedAccount(block, owner, token)
      }

    } yield updatedOrders
  }

  private def cancelOrderInternal(
      status: OrderStatus,
      blockOpt: Option[Long] = None,
      skipProcessingUpdatedOrders: Boolean = false
    )(orders: Iterable[Matchable]
    ) = {
    val statusIsInvalid = status match {
      case STATUS_EXPIRED | //
          STATUS_DUST_ORDER | //
          STATUS_COMPLETELY_FILLED | //
          STATUS_SOFT_CANCELLED_BY_USER | //
          STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR | //
          STATUS_ONCHAIN_CANCELLED_BY_USER | //
          STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR | //
          STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES | //
          STATUS_SOFT_CANCELLED_LOW_BALANCE | //
          STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE | //
          STATUS_SOFT_CANCELLED_BY_DISABLED_MARKET | //
          STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS | //
          STATUS_SOFT_CANCELLED_DUPLICIATE =>
        false

      case _ => true
    }

    if (statusIsInvalid) {
      Future.failed(ErrorException(ERR_INTERNAL_UNKNOWN, status.toString))
    } else {
      for {
        _ <- serializeFutures(orders) { order =>
          onToken(order.tokenS, _.release(order.id)).andThen {
            case _ =>
              orderPool += order.copy(
                block = order.block.max(blockOpt.getOrElse(0L)),
                status = status
              )
          }
        }
        updatedOrders = orderPool.takeUpdatedOrders
        _ <- {
          if (skipProcessingUpdatedOrders) Future.unit
          else
            updatedOrdersProcessor.processUpdatedOrders(
              blockOpt.isDefined,
              updatedOrders
            )
        }
      } yield updatedOrders
    }
  }

  private def reserveForOrder(order: Matchable): Future[(Long, Set[String])] = {
    val requestedAmountS = order.requestedAmount(order.tokenS)
    val requestedAmountFee = order.requestedAmount(order.tokenFee)

    if (requestedAmountS <= 0 || requestedAmountFee < 0) {
      orderPool += order.copy(status = STATUS_INVALID_DATA)
      Future.successful((0L, Set(order.id)))
    } else
      for {
        _ <- Future.unit
        (b1, r1) <- onToken(order.tokenS, _.reserve(order.id, requestedAmountS))
        (b2, r2) <- {
          if (r1.contains(order.id) || order.tokenFee == order.tokenS || requestedAmountFee == 0)
            Future.successful((b1, Set.empty[String]))
          else {
            onToken(order.tokenFee, _.reserve(order.id, requestedAmountFee))
          }
        }
        block = b1.max(b2)
        orderIdsToDelete = r1 ++ r2
      } yield (block, orderIdsToDelete)
  }

  private def getReserveManagers(
      tokens_ : Set[String],
      mustReturn: Boolean
    ): Future[Map[String, ReserveManager]] = {
    val (existing, missing) = tokens_.partition(tokens.contains)
    val existingManagers =
      existing.map(tokens.apply).map(m => m.token -> m).toMap
    if (!mustReturn) Future.successful(existingManagers)
    else {
      for {
        balanceAndAllowances <- Future.sequence {
          missing.map { token =>
            provider.getBalanceAndALlowance(owner, token)
          }
        }
        tuples = missing.zip(balanceAndAllowances)
        newManagers = tuples.map {
          case (token, (block, balance, allowance)) =>
            val manager = ReserveManager.default(token, enableTracing)
            manager.setBalanceAndAllowance(block, balance, allowance)
            tokens += token -> manager
            token -> manager
        }.toMap
      } yield newManagers ++ existingManagers
    }
  }

  // Do not use getReserveManagers for best performance
  private def getReserveManagerOption(
      token: String,
      mustReturn: Boolean
    ): Future[Option[ReserveManager]] = {
    if (tokens.contains(token)) Future.successful(Some(tokens(token)))
    else if (!mustReturn) Future.successful(None)
    else {
      provider.getBalanceAndALlowance(owner, token).map { result =>
        val (block, balance, allowance) = result
        val manager = ReserveManager.default(token, enableTracing)
        manager.setBalanceAndAllowance(block, balance, allowance)
        tokens += token -> manager
        Some(manager)
      }
    }
  }

  private def onToken(
      token: String,
      invoke: ReserveManagerMethod
    ): Future[(Long, Set[String])] =
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      orderIdsToDelete = invoke(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)
      // we cannot parallel execute these following operations
      _ <- serializeFutures(ordersToDelete) { order =>
        if (token == order.tokenS) Future.unit
        else {
          getReserveManagerOption(order.tokenS, false).map { managerOpt =>
            managerOpt.foreach(_.release(order.id))
          }
        }
      }
      _ <- serializeFutures(ordersToDelete) { order =>
        if (token == order.tokenFee) Future.unit
        else {
          getReserveManagerOption(order.tokenFee, false).map { managerOpt =>
            managerOpt.foreach(_.release(order.id))
          }
        }
      }
    } yield (manager.getBlock, orderIdsToDelete)

}
