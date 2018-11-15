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

package org.loopring.lightcone.actors.actor

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.actors.routing.Routers
import org.loopring.lightcone.proto.actors.{ CancelOrderReq, CancelOrderRes, ErrorCode, GetBalanceAndAllowancesReq, GetBalanceAndAllowancesRes, OrderStatus, SubmitOrderReq, SubmitOrderRes, UpdateBalanceAndAllowanceReq, UpdateBalanceAndAllowanceRes, UpdateFilledAmountReq, UpdateFilledAmountRes }
import org.loopring.lightcone.core.{ Order ⇒ COrder, _ }
import org.loopring.lightcone.proto.deployment.OrderManagerSettings
import org.loopring.lightcone.actors.base

import scala.concurrent.{ ExecutionContext, Future }

object OrderManagingActor
  extends base.Deployable[OrderManagerSettings] {
  val name = "order_managing_actor"

  def getCommon(s: OrderManagerSettings) =
    base.CommonSettings(None, s.roles, s.instances)
}

class OrderManagingActor(
  owner: Address)(
  implicit
  dustOrderEvaluator: DustOrderEvaluator,
  ec: ExecutionContext,
  timeout: Timeout)
  extends Actor
  with ActorLogging {

  implicit val orderPool = new OrderPoolImpl
  val updatedOrderReceiver = new UpdatedOrderReceiver()
  orderPool.addCallback(updatedOrderReceiver.onUpdatedOrder)
  val manager: OrderManager = OrderManager.default(10000)

  // todo: 如何初始化？在本actor初始化订单数据还是在其他actor获取所有数据后批量发送过来？？？

  // todo: 返回到上一层调用时status到error的映射关系
  def receive() = LoggingReceive {
    case SubmitOrderReq(orderOpt) ⇒
      assert(orderOpt.nonEmpty)
      val reqOrder = orderOpt.get.toPojo
      assert(reqOrder.outstanding.amountS > 0)

      // 必须重新赋值, 否则map找不到actor,数据会发到deadLetter
      val _sender = sender()

      for {
        _ ← Future.sequence(Seq(
          getTokenManagerAsFuture(reqOrder.tokenS),
          getTokenManagerAsFuture(reqOrder.tokenFee)))
        success = manager.submitOrder(reqOrder)
        order = updatedOrderReceiver.takeOrders.head
        _ = if (success) tellMarketManager(order)
        pOrder = order.toProto
        errCode: ErrorCode = pOrder.status
      } yield {
        _sender ! SubmitOrderRes(errCode, Some(pOrder))
      }

    case CancelOrderReq(id, hardCancel) ⇒
      val errorCode = manager.cancelOrder(id) match {
        case true ⇒
          updatedOrderReceiver.takeOrders.map(tellMarketManager)
          ErrorCode.OK

        case false ⇒
          ErrorCode.ORDER_NOT_EXIST
      }
      sender() ! CancelOrderRes(id, hardCancel, errorCode)

    // amountS为剩余量
    case UpdateFilledAmountReq(id, amountS) ⇒
      val errorCode = manager.adjustOrder(id, amountS) match {
        case true ⇒
          updatedOrderReceiver.takeOrders.map(tellMarketManager)
          ErrorCode.OK

        case false ⇒
          ErrorCode.ORDER_NOT_EXIST
      }
      sender() ! UpdateFilledAmountRes(errorCode)

    // TODO: 这里不应该是一个req, 返回值只用于测试
    case UpdateBalanceAndAllowanceReq(address, token, accountOpt) ⇒
      val errorCode = if (manager.hasTokenManager(token)) {
        assert(accountOpt.nonEmpty)
        val account = accountOpt.get
        val tokenManager = manager.getTokenManager(token)
        tokenManager.init(account.balance, account.allowance)
        updatedOrderReceiver.takeOrders.map(tellMarketManager)
        ErrorCode.OK
      } else {
        ErrorCode.TOKEN_NOT_EXIST
      }
      sender() ! UpdateBalanceAndAllowanceRes(errorCode)

  }

  private def getTokenManagerAsFuture(token: Address): Future[TokenManager] = {
    if (manager.hasTokenManager(token)) {
      Future.successful(manager.getTokenManager(token))
    } else {
      val tm = manager.addTokenManager(new TokenManager(token, 10000))
      (Routers.ethereumAccessActor ? GetBalanceAndAllowancesReq(owner, Seq(token)))
        .mapTo[GetBalanceAndAllowancesRes].map(_.balanceAndAllowanceMap(token)).map {
          ba ⇒
            tm.init(ba.balance, ba.allowance)
            tm
        }
    }
  }

  private def tellMarketManager(order: COrder) = {
    val marketId = MarketId(order.tokenS, order.tokenB)
    Routers.marketManagingActor ! SubmitOrderReq(Some(order.toProto()))
  }

  implicit private def orderStatus2ErrorCode(status: OrderStatus): ErrorCode = status match {
    case OrderStatus.CANCELLED_LOW_BALANCE ⇒ ErrorCode.LOW_BALANCE
    case OrderStatus.CANCELLED_LOW_FEE_BALANCE ⇒ ErrorCode.LOW_FEE_BALANCE
    case OrderStatus.CANCELLED_TOO_MANY_ORDERS ⇒ ErrorCode.TOO_MANY_ORDERS
    case OrderStatus.CANCELLED_TOO_MANY_FAILED_SETTLEMENTS ⇒ ErrorCode.TOO_MANY_FAILED_MATCHES
    case _ ⇒ ErrorCode.OK
  }
}

class UpdatedOrderReceiver {
  private var receivedOrders = Seq.empty[COrder]

  def onUpdatedOrder(order: COrder) = {
    receivedOrders :+= order
  }

  def takeOrders(): Seq[COrder] = this.synchronized {
    // assert(receivedOrders.size > 0)
    val ret = receivedOrders
    receivedOrders = Seq.empty
    ret
  }
}
