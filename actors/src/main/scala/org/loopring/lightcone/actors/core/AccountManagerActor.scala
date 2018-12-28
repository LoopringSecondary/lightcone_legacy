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

import akka.actor.SupervisorStrategy._
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
import org.loopring.lightcone.core.data.Matchable
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._

import scala.concurrent._
import scala.concurrent.duration._

// main owner: 于红雨
class AccountManagerActor(
    address: String
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator,
    val dbModule: DatabaseModule)
    extends Actor
    with ActorLogging {

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception ⇒
        log.error(e.getMessage)
        Escalate
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default

  protected def ethereumQueryActor = actors.get(EthereumQueryActor.name)
  protected def marketManagerActor = actors.get(MarketManagerActor.name)

  def receive: Receive = LoggingReceive {

    case XRecover.RecoverOrderReq(Some(xraworder)) =>
      submitOrder(xraworder).map { _ =>
        XRecover.RecoverOrderRes(xraworder.id, true)
      }.sendTo(sender)

    case XGetBalanceAndAllowancesReq(addr, tokens) =>
      assert(addr == address)
      (for {
        managers <- getTokenManagers(tokens)
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

    case XSubmitSimpleOrderReq(_, Some(xorder)) =>
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
    val matchable: Matchable = xorder
    for {
      _ <- getTokenManager(matchable.tokenS)
      _ <- if (matchable.amountFee > 0 && matchable.tokenS != matchable.tokenFee)
        getTokenManager(matchable.tokenFee)
      else
        Future.successful(Unit)

      // Update the order's _outstanding field.
      getFilledAmountRes <- (ethereumQueryActor ? XGetFilledAmountReq(
        Seq(matchable.id)
      )).mapAs[XGetFilledAmountRes]

      _ = log.debug(s"order history: orderHistoryRes")

      _matchable = matchable.withFilledAmountS(
        getFilledAmountRes.filledAmountSMap(matchable.id)
      )
      _ = log.info(s"submitting order to AccountManager: ${_matchable}")
      (successful, updatedOrders) = manager.submitAndGetUpdatedOrders(
        _matchable
      )
      _ = assert(updatedOrders.contains(_matchable.id))
      _ = log.debug(s"assert contains order:  ${updatedOrders(_matchable.id)}")
      _ = if (!successful)
        throw ErrorException(XError(matchable.status))
      res <- Future.sequence {
        updatedOrders.map { o =>
          for {
            //需要更新到数据库
            _ <- dbModule.orderService.updateOrderStatus(o._2.id, o._2.status)
          } yield {
            marketManagerActor ! XSubmitSimpleOrderReq(
              order = Some(o._2.copy(_reserved = None, _outstanding = None))
            )
          }
        }
      }
      matchable_ = updatedOrders(_matchable.id)
      order_ : XOrder = matchable_.copy(_reserved = None, _outstanding = None)
    } yield XSubmitOrderRes(order = Some(order_))
  }

  private def getTokenManager(token: String): Future[AccountTokenManager] = {
    if (manager.hasTokenManager(token)) {
      Future.successful(manager.getTokenManager(token))
    } else {
      log.debug(s"getTokenManager0 ${token}")
      for {
        res <- (ethereumQueryActor ? XGetBalanceAndAllowancesReq(
          address,
          Seq(token)
        )).mapAs[XGetBalanceAndAllowancesRes]
        tm = new AccountTokenManagerImpl(
          token,
          config.getInt("account_manager.max_order_num")
        )
        ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
        _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        tokenManager = manager.getOrUpdateTokenManager(token, tm)
        _ = log.debug(s"getTokenManager5 ${token}")

      } yield tokenManager
    }
  }

  private def getTokenManagers(
      tokens: Seq[String]
    ): Future[Seq[AccountTokenManager]] = {
    val tokensWithoutMaster =
      tokens.filterNot(token ⇒ manager.hasTokenManager(token))
    for {
      res <- if (tokensWithoutMaster.nonEmpty) {
        (ethereumQueryActor ? XGetBalanceAndAllowancesReq(
          address,
          tokensWithoutMaster
        )).mapAs[XGetBalanceAndAllowancesRes]
      } else {
        Future.successful(XGetBalanceAndAllowancesRes())
      }
      tms = tokensWithoutMaster.map(
        token ⇒
          new AccountTokenManagerImpl(
            token,
            config.getInt("account_manager.max_order_num")
          )
      )
      _ = tms.foreach(tm ⇒ {
        val ba = res.balanceAndAllowanceMap(tm.token)
        tm.setBalanceAndAllowance(ba.balance, ba.allowance)
        manager.addTokenManager(tm)
      })
      tokenMangers ← Future.sequence(tokens.map(getTokenManager))
    } yield tokenMangers
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
      _ <- Future.sequence {
        updatedOrders.map { order =>
          order.status match {
            case STATUS_CANCELLED_LOW_BALANCE |
                STATUS_CANCELLED_LOW_FEE_BALANCE =>
              for {
                _ <- dbModule.orderService
                  .updateOrderStatus(order.id, order.status)
              } yield {
                marketManagerActor ! XCancelOrderReq(
                  id = order.id,
                  marketId = Some(XMarketId(order.tokenS, order.tokenB))
                )
              }
            case STATUS_PENDING =>
              //allowance的改变需要更新到marketManager
              for {
                _ <- marketManagerActor ? XSubmitSimpleOrderReq(
                  order = Some(order)
                )
              } yield Unit

            case status =>
              log.error(
                s"unexpected order status caused by balance/allowance upate: $status"
              )
              throw ErrorException(
                XErrorCode.ERR_INTERNAL_UNKNOWN,
                s"unexpected order status caused by balance/allowance upate: $status"
              )
          }
        }
      }
    } yield Unit

}
