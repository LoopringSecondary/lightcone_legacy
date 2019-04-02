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

import io.lightcone.lib.TimeProvider
import org.slf4s.Logging
import scala.concurrent._
import io.lightcone.lib.FutureUtil._

// This class is not thread safe.
// TODO(dongw): use ConcurrentHashMap
final class AccountManagerImpl(
    val owner: String,
    val balanceRefreshIntervalSeconds: Int,
    val enableTracing: Boolean = false
  )(
    implicit
    updatedOrdersProcessor: UpdatedOrdersProcessor,
    updatedAccountsProcessor: UpdatedAccountsProcessor,
    timeProvider: TimeProvider,
    balanceProvider: BalanceAndAllowanceProvider,
    ec: ExecutionContext)
    extends AccountManager
    with Logging {

  import OrderStatus._
  import ErrorCode._

  type ReserveManagerMethod = ReserveManager => Set[String]
  private val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  private implicit var tokens = Map.empty[String, ReserveManager]

  private var block = 0L
  private var marketPairCutoffs = Map.empty[String, Long]
  private var ownerCutoff: Long = 0L

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
    ) = {
    this.block = block
    setBalanceAndAllowanceInternal(token) {
      _.setBalanceAndAllowance(block, balance, allowance)
    }
  }

  def setBalance(
      block: Long,
      token: String,
      balance: BigInt
    ) = {
    this.block = block
    setBalanceAndAllowanceInternal(token) {
      _.setBalance(block, balance)
    }
  }

  def setAllowance(
      block: Long,
      token: String,
      allowance: BigInt
    ) = {
    this.block = block
    setBalanceAndAllowanceInternal(token) {
      _.setAllowance(block, allowance)
    }
  }

  def resubmitOrder(order: Matchable) = {

    for {
      (successful, updatedOrders) <- if (order.status.isCancelledStatus()) {
        Future.successful(false, Map(order.id -> order))
      } else {
        for {
          _ <- Future.unit
          order_ = order.copy(
            _reserved = None,
            _actual = None,
            _matchable = None
          )
          _ = { orderPool += order_.as(STATUS_PENDING) } // potentially replace the old one.
          (block, orderIdsToDelete) <- reserveForOrder(order_)
          ordersToDelete = orderIdsToDelete.map {
            case (id, _) => orderPool.apply(id)
          }
          _ = ordersToDelete.foreach { order =>
            orderPool +=
              orderPool(order.id).copy(
                block = order.block.max(block),
                status = orderIdsToDelete(order.id)
              )
          }
          _ = log.debug(
            s"AccountManagerImpl -- resubmitOrder -- orderIdsToDelete ${orderIdsToDelete} "
          )
          successful = !orderIdsToDelete.contains(order.id)
          _ = if (successful) {
            orderPool += orderPool(order_.id).copy(status = STATUS_PENDING)
          }
          updatedOrders = orderPool.takeUpdatedOrders
        } yield (successful, updatedOrders)
      }
      _ <- updatedOrdersProcessor.processUpdatedOrders(true, updatedOrders)
    } yield (successful, updatedOrders)
  }

  def cancelOrder(
      orderId: String,
      status: OrderStatus = STATUS_SOFT_CANCELLED_BY_USER
    ) =
    for {
      orders <- cancelOrderInternal(status)(orderPool.getOrder(orderId).toSeq)
    } yield (orders.nonEmpty, orders)

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

  def doesOrderSatisfyCutoff(
      orderValidSince: Long,
      marketHash: String
    ): Boolean = {
    ownerCutoff < orderValidSince &&
    marketPairCutoffs.getOrElse(marketHash, 0L) < orderValidSince
  }

  def setCutoff(
      block: Long,
      cutoff: Long,
      marketHash: Option[String]
    ) = {
    this.block = block

    marketHash match {
      case Some(mh) if mh.nonEmpty =>
        marketPairCutoffs += (mh -> cutoff)
        cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER, Some(block)) {
          orderPool.orders.filter { order =>
            order.validSince <= cutoff &&
            MarketHash(MarketPair(order.tokenS, order.tokenB)).hashString == mh
          }
        }

      case _ =>
        this.ownerCutoff = cutoff
        cancelOrderInternal(STATUS_ONCHAIN_CANCELLED_BY_USER, Some(block)) {
          orderPool.orders.filter(_.validSince <= cutoff)
        }
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
      token: String
    )(method: ReserveManager => Set[String]
    ): Future[Map[String, Matchable]] = {
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      lastBlock = manager.getBlock
      orderIdsToDelete = method(manager)
      ordersToDelete = orderIdsToDelete.map(orderPool.apply)

      // release tokenFee allocations if tokenS != tokenFee
      _ <- serializeFutures(ordersToDelete) { order =>
        if (order.tokenFee == order.tokenS) Future.unit
        else
          for {
            managerFeeOpt <- getReserveManagerOption(order.tokenFee, false)
            _ = managerFeeOpt.foreach(_.release(order.id))
          } yield Unit
      }

      // Make sure order's block and status are updated
      _ = ordersToDelete.foreach { order =>
        orderPool += order.copy(
          block = block,
          status = STATUS_SOFT_CANCELLED_LOW_BALANCE
        )
      }

      updatedOrders = orderPool.takeUpdatedOrders
      _ <- updatedOrdersProcessor.processUpdatedOrders(true, updatedOrders)

      // track account update only when block number changed
      _ <- {
        if (lastBlock == block) Future.unit
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
          for {
            managerSOpt <- getReserveManagerOption(order.tokenS, false)
            r1 = managerSOpt.map(_.release(order.id))

            managerFeeOpt <- {
              if (order.tokenS == order.tokenFee) Future.successful(None)
              else getReserveManagerOption(order.tokenFee, false)
            }

            _ = managerFeeOpt.map(_.release(order.id))

            _ = orders.foreach { order =>
              orderPool +=
                order.copy(
                  block = order.block.max(blockOpt.getOrElse(0)),
                  status = status
                )
            }

          } yield Unit
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

  private def reserveToken(
      token: String,
      orderId: String,
      requestedAmountS: BigInt
    ): Future[(Long, Set[String])] =
    for {
      managerOpt <- getReserveManagerOption(token, true)
      manager = managerOpt.get
      orderIdsToDelete = manager.reserve(orderId, requestedAmountS)
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

  private def reserveForOrder(
      order: Matchable
    ): Future[(Long, Map[String, OrderStatus])] = {
    val requestedAmountS = order.requestedAmount(order.tokenS)
    val requestedAmountFee = order.requestedAmount(order.tokenFee)

    if (requestedAmountS <= 0 || requestedAmountFee < 0) {
//      orderPool += order.copy(status = STATUS_INVALID_DATA)
      //需要释放该订单相关的余额等，虽然通过返回再提交到MarketManager可以实现取消订单，但是，会在Actor中改变与BalanceUpdateEvent的顺序
      for {
        reserveManagers <- Future.sequence(
          if (order.tokenS == order.tokenFee)
            Seq(getReserveManagerOption(order.tokenS, false))
          else
            Seq(
              getReserveManagerOption(order.tokenS, false),
              getReserveManagerOption(order.tokenFee, false)
            )
        )
        _ = reserveManagers.map(_.map(_.release(order.id)))
      } yield {
        if (requestedAmountS == 0)
          (0L, Map(order.id -> STATUS_PENDING)) //因为目前订单的金额等控制是在MarketManager中，因此，设置成PENDING，便于提交到market，
        else
          (0L, Map(order.id -> STATUS_INVALID_DATA))
      }
    } else
      for {
        _ <- Future.unit
        (b1, r1) <- reserveToken(order.tokenS, order.id, requestedAmountS)
        (b2, r2) <- {
          if (r1.contains(order.id) || order.tokenFee == order.tokenS || requestedAmountFee == 0)
            Future.successful((b1, Set.empty[String]))
          else {
            reserveToken(order.tokenFee, order.id, requestedAmountFee)
          }
        }
        block = b1.max(b2)
        orderIdsToDelete = r1.map(_ -> STATUS_SOFT_CANCELLED_LOW_BALANCE) ++
          r2.map(_ -> STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE)
      } yield (block, orderIdsToDelete.toMap)
  }

  private def getReserveManagers(
      tokens_ : Set[String],
      mustReturn: Boolean
    ): Future[Map[String, ReserveManager]] = {
    val (existing, missing) = tokens_.partition(tokens.contains)
    val existingManagers =
      existing.map(tokens.apply).map(m => m.token -> m).toMap

    if (!mustReturn) Future.successful(existingManagers)
    else
      for {
        balanceAndAllowances <- Future.sequence {
          missing.map { token =>
            balanceProvider.getBalanceAndALlowance(owner, token)
          }
        }
        newManagers = missing
          .zip(balanceAndAllowances)
          .map {
            case (token, (block, balance, allowance)) =>
              log.debug(
                s"fetched balance and allowance for new reserve manager for token: $token => " +
                  s"block: $block, balance: $balance, allowance: $allowance"
              )
              val manager = ReserveManager.default(
                token,
                balanceRefreshIntervalSeconds,
                enableTracing
              )
              manager.setBalanceAndAllowance(block, balance, allowance)
              tokens += token -> manager
              token -> manager
          }
          .toMap

        expiredManagers = existingManagers.filter(_._2.needRefresh)

        _ <- Future.sequence {
          expiredManagers.map {
            case (token, manager) =>
              for {
                (block, balance, allowance) <- balanceProvider
                  .getBalanceAndALlowance(owner, token)
                _ = log.debug(
                  s"fetched balance and allowance for expired reserve manager for token: $token =>" +
                    s"block: $block, balance: $balance, allowance: $allowance"
                )
                _ = manager.setBalanceAndAllowance(block, balance, allowance)
              } yield Unit
          }
        }

      } yield newManagers ++ existingManagers
  }

  @inline private def getReserveManagerOption(
      token: String,
      mustReturn: Boolean
    ): Future[Option[ReserveManager]] =
    getReserveManagers(Set(token), mustReturn)
      .map(_.get(token))

}
