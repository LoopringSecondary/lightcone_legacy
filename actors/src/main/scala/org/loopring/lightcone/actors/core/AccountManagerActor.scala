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
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.persistence._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.actors.XErrorCode._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core.XOrderStatus._
import org.loopring.lightcone.proto.core._

import scala.concurrent._
import scala.concurrent.duration._

//仅在core中使用，只能被AccountManagerSharding初始化
private[core] object AccountManagerActor {
  val name: String = "account_manager"
}

private[core] class AccountManagerActor(
    val actors: Lookup[ActorRef]
)(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    val dustEvaluator: DustOrderEvaluator
)
  extends Actor
  with ActorLogging {

  //作为ShardingActor的子Actor，遇到异常时要将错误抛给父Actor，由父Actor重启所有的子Actor
  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case _: Exception ⇒ Escalate //所有异常都抛给上层监管者，shardingActor
    }

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
  val manager = AccountManager.default
  private var address: String = _ //todo:创建该actor的请求中的地址
  private var recoveryEnd: Boolean = false //todo:创建该actor后，是否已经恢复过，否则先执行恢复逻辑，这期间的消息都放到stash中

  protected def ordersRecoveryActor = actors.get(OrdersRecoveryActor.name)
  protected def ordersDalActor = actors.get(OrdersDalActor.name) //todo:订单的更新、余额引起的取消等都需要写入数据库
  protected def accountBalanceActor = actors.get(AccountBalanceActor.name)
  protected def orderHistoryActor = actors.get(OrderStateActor.name)
  protected def marketManagerActor = actors.get(MarketManagerActor.name)
  protected def accountManagerSharding = actors.get(AccountManagerShardingActor.name)

  def receive: Receive = LoggingReceive {

    case XGetBalanceAndAllowancesReq(addr, tokens) ⇒
      assert(addr == address)

      (for {
        managers ← Future.sequence(tokens.map(getTokenManager))
        _ = assert(tokens.size == managers.size)
        balanceAndAllowanceMap = tokens.zip(managers).toMap.map {
          case (token, manager) ⇒ token -> XBalanceAndAllowance(
            manager.getBalance(),
            manager.getAllowance(),
            manager.getAvailableBalance(),
            manager.getAvailableAllowance()
          )
        }
      } yield {
        XGetBalanceAndAllowancesRes(address, balanceAndAllowanceMap)
      }).pipeTo(sender)

    case XSubmitOrderReq(Some(xorder)) ⇒
      submitOrder(xorder).pipeTo(sender)

    case req: XCancelOrderReq ⇒
      //todo:恢复时，需要直接发送到marketmanager
      if (manager.cancelOrder(req.id)) {
        marketManagerActor forward req
      } else {
        sender ! XCancelOrderRes(error = ERR_ORDER_NOT_EXIST)
      }

    case XAddressBalanceUpdated(_, token, newBalance) ⇒
      updateBalanceOrAllowance(token, newBalance, _.setBalance(_))

    case XAddressAllowanceUpdated(_, token, newBalance) ⇒
      updateBalanceOrAllowance(token, newBalance, _.setAllowance(_))

    case msg ⇒ log.debug(s"unknown msg ${msg}")
  }

  private def submitOrder(xorder: XOrder): Future[XSubmitOrderRes] = {
    val order: Order = xorder
    for {
      _ ← getTokenManager(order.tokenS)
      _ ← getTokenManager(order.tokenFee) if order.amountFee > 0 && order.tokenS != order.tokenFee

      // Update the order's _outstanding field.
      orderHistoryRes ← (orderHistoryActor ? XGetOrderFilledAmountReq(order.id))
        .mapTo[XGetOrderFilledAmountRes]

      _ = log.debug(s"order history: orderHistoryRes")

      _order = order.withFilledAmountS(orderHistoryRes.filledAmountS)

      _ = log.debug(s"submitting order to AccountManager: ${_order}")
      successful = manager.submitOrder(_order)
      _ = log.info(s"successful: $successful")
      _ = log.info("orderPool updatdOrders: " + orderPool.getUpdatedOrders.mkString(", "))
      updatedOrders = orderPool.takeUpdatedOrdersAsMap()
      _ = assert(updatedOrders.contains(_order.id))
      order_ = updatedOrders(_order.id)
      xorder_ : XOrder = order_.copy(_reserved = None, _outstanding = None)
    } yield {
      //todo:order_与updatedOrders都需要判断是否需要取消的，因为如果没有successful，说明订单取消，但是需要尝试向MMA发送消息尝试取消的
      if (successful) {
        log.debug(s"submitting order to market manager actor: $order_")
        marketManagerActor ! XSubmitOrderReq(Some(xorder_))
        XSubmitOrderRes(order = Some(xorder_))
      } else {
        val error = convertOrderStatusToErrorCode(order.status)
        marketManagerActor ! XCancelOrderReq(xorder_.id, false, order.status)
        XSubmitOrderRes(error = error)
      }
    }
  }

  private def convertOrderStatusToErrorCode(status: XOrderStatus): XErrorCode = status match {
    case STATUS_INVALID_DATA ⇒ ERR_INVALID_ORDER_DATA
    case STATUS_UNSUPPORTED_MARKET ⇒ ERR_INVALID_MARKET
    case STATUS_CANCELLED_TOO_MANY_ORDERS ⇒ ERR_TOO_MANY_ORDERS
    case STATUS_CANCELLED_DUPLICIATE ⇒ ERR_ORDER_ALREADY_EXIST
    case _ ⇒ ERR_UNKNOWN
  }

  private def getTokenManager(token: String): Future[AccountTokenManager] = {
    if (manager.hasTokenManager(token))
      Future.successful(manager.getTokenManager(token))
    else for {
      _ ← Future.successful(log.debug(s"getTokenManager0 ${token}"))
      res ← (accountBalanceActor ? XGetBalanceAndAllowancesReq(address, Seq(token)))
        .mapTo[XGetBalanceAndAllowancesRes]
      tm = new AccountTokenManagerImpl(token, 1000)
      ba: BalanceAndAllowance = res.balanceAndAllowanceMap(token)
      _ = tm.setBalanceAndAllowance(ba.balance, ba.allowance)
      tokenManager = manager.addTokenManager(tm)
      _ ← Future.successful(log.debug(s"getTokenManager5 ${token}"))

    } yield tokenManager
  }

  private def updateBalanceOrAllowance(
    token: String,
    amount: BigInt,
    method: (AccountTokenManager, BigInt) ⇒ Unit
  ) = for {
    tm ← getTokenManager(token)
    _ = method(tm, amount)
    updatedOrders = orderPool.takeUpdatedOrders()
  } yield {
    updatedOrders.foreach { order ⇒
      order.status match {
        case STATUS_CANCELLED_LOW_BALANCE | STATUS_CANCELLED_LOW_FEE_BALANCE ⇒
          marketManagerActor ! XCancelOrderReq(order.id, false, order.status)

        case STATUS_PENDING ⇒
          //allowance的改变需要更新到marketManager
          marketManagerActor ! XSubmitOrderReq(Some(order))

        case status ⇒
          log.error(s"unexpected order status caused by balance/allowance upate: $status")
      }
    }
  }

  protected def recoverOrder(xorder: XOrder) = {
    log.debug(s"recoverOrder, ${self.path.toString}, ${orderHistoryActor.path.toString}, ${xorder}")
    submitOrder(xorder)
  }

}

