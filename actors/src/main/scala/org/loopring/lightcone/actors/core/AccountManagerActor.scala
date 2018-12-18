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

package org.loopring.lightcone.actors.core

import akka.actor.SupervisorStrategy.Escalate
import akka.actor._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._

import scala.concurrent._
import scala.concurrent.duration._

// main owner: 于红雨
object AccountManagerActor {
  val name = "account_manager"
}

class AccountManagerActor(
    address: String
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends Actor
    with ActorLogging {

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      //所有异常都抛给上层监管者，shardingActor
      case e: Exception ⇒
        log.error(e.getMessage)

        Escalate
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default

  protected def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  protected def marketManagerActor = actors.get(MarketManagerActor.name)

  def receive: Receive = LoggingReceive {

    case XGetBalanceAndAllowancesReq(addr, tokens) =>
      assert(addr == address)
      (for {
        managers <- Future.sequence(tokens.map(getTokenManager))
        _ = assert(tokens.size == managers.size)
        balanceAndAllowanceMap = tokens.zip(managers).toMap.map {
          case (token, manager) =>
            token -> XBalanceAndAllowance(
              manager.getBalance(),
              manager.getAllowance(),
              manager.getAvailableBalance(),
              manager.getAvailableAllowance()
            )
        }
      } yield {
        XGetBalanceAndAllowancesRes(address, balanceAndAllowanceMap)
      }).sendTo(sender)

    case XSubmitSimpleOrderReq(addr, Some(xorder)) =>
      assert(addr == address)
      submitOrder(xorder).sendTo(sender)

    case req: XCancelOrderReq =>
      assert(req.owner == address)
      if (manager.cancelOrder(req.id)) {
        marketManagerActor forward req
      } else {
        Future.failed(
          ErrorException(
            ERR_FAILED_HANDLE_MSG,
            s"no order found with id: ${req.id}"
          )
        ) sendTo sender
      }

    case XAddressBalanceUpdated(addr, token, newBalance) =>
      assert(addr == address)
      updateBalanceOrAllowance(token, newBalance, _.setBalance(_))

    case XAddressAllowanceUpdated(addr, token, newBalance) =>
      assert(addr == address)
      updateBalanceOrAllowance(token, newBalance, _.setAllowance(_))
  }

  private def submitOrder(xorder: XOrder): Future[XSubmitOrderRes] = {
    val order: Order = xorder
    for {
      _ <- getTokenManager(order.tokenS)
      _ <- if (order.amountFee > 0 && order.tokenS != order.tokenFee)
        getTokenManager(order.tokenFee)
      else
        Future.successful(Unit)

      // Update the order's _outstanding field.
      orderHistoryRes <- (ethereumQueryActor ? XGetOrderFilledAmountReq(
        order.id
      )).mapAs[XGetOrderFilledAmountRes]

      _ = log.debug(s"order history: orderHistoryRes")

      _order = order.withFilledAmountS(orderHistoryRes.filledAmountS)

      _ = log.debug(s"submitting order to AccountManager: ${_order}")
      successful = manager.submitOrder(_order)
      _ = log.debug(s"successful: $successful")
      _ = log.debug(
        "orderPool updatdOrders: " + orderPool.getUpdatedOrders.mkString(", ")
      )
      updatedOrders = orderPool.takeUpdatedOrdersAsMap()
      _ = assert(updatedOrders.contains(_order.id))
      order_ = updatedOrders(_order.id)
      xorder_ : XOrder = order_.copy(_reserved = None, _outstanding = None)
    } yield {
      if (successful) {
        log.debug(s"submitting order to market manager actor: $order_")
        marketManagerActor ! XSubmitSimpleOrderReq("", Some(xorder_))
        XSubmitOrderRes(order = Some(xorder_))
      } else {
        throw new ErrorException(
          XError(convertOrderStatusToErrorCode(order.status))
        )
      }
    }
  }

  private def convertOrderStatusToErrorCode(status: XOrderStatus): XErrorCode =
    status match {
      case STATUS_INVALID_DATA              => ERR_INVALID_ORDER_DATA
      case STATUS_UNSUPPORTED_MARKET        => ERR_INVALID_MARKET
      case STATUS_CANCELLED_TOO_MANY_ORDERS => ERR_TOO_MANY_ORDERS
      case STATUS_CANCELLED_DUPLICIATE      => ERR_ORDER_ALREADY_EXIST
      case _                                => ERR_INTERNAL_UNKNOWN
    }

  private def getTokenManager(token: String): Future[AccountTokenManager] = {
    if (manager.hasTokenManager(token))
      Future.successful(manager.getTokenManager(token))
    else
      for {
        _ <- Future.successful(log.debug(s"getTokenManager0 ${token}"))
        res <- (ethereumQueryActor ? XGetBalanceAndAllowancesReq(
          address,
          Seq(token)
        )).mapAs[XGetBalanceAndAllowancesRes]
        tm = new AccountTokenManagerImpl(token, 1000)
        ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
        _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        tokenManager = manager.addTokenManager(tm)
        _ <- Future.successful(log.debug(s"getTokenManager5 ${token}"))

      } yield tokenManager
  }

  private def updateBalanceOrAllowance(
      token: String,
      amount: BigInt,
      method: (AccountTokenManager, BigInt) => Unit
    ) =
    for {
      tm <- getTokenManager(token)
      _ = method(tm, amount)
      updatedOrders = orderPool.takeUpdatedOrders()
    } yield {
      updatedOrders.foreach { order =>
        order.status match {
          case STATUS_CANCELLED_LOW_BALANCE |
              STATUS_CANCELLED_LOW_FEE_BALANCE =>
            marketManagerActor ! XCancelOrderReq(
              id = order.id,
              marketName = XMarketId(order.tokenS, order.tokenB)
            )

          case STATUS_PENDING =>
            //allowance的改变需要更新到marketManager
            marketManagerActor ! XSubmitSimpleOrderReq(order = Some(order))

          case status =>
            log.error(
              s"unexpected order status caused by balance/allowance upate: $status"
            )
        }
      }
    }

  protected def recoverOrder(xorder: XOrder) = {
    log.debug(
      s"recoverOrder, ${self.path.toString}, ${ethereumQueryActor.path.toString}, ${xorder}"
    )
    submitOrder(xorder)
  }

}
