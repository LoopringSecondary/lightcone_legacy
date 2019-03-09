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

package io.lightcone.relayer.actors

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data._
import scala.concurrent._

trait AccountManagerProcessors {
  me: Actor with ActorLogging =>

  implicit val dbModule: DatabaseModule
  implicit val ec: ExecutionContext
  implicit val timeout: Timeout

  val owner: String

  import ErrorCode._
  import OrderStatus._

  def marketManagerActor: ActorRef
  def chainReorgManagerActor: ActorRef
  def socketIONotifierActor: ActorRef

  implicit val updatedOrdersProcessor = new UpdatedOrdersProcessor()(ec) {

    def processUpdatedOrder(
        trackOrderUpdated: Boolean,
        order: Matchable
      ): Future[Any] = {
      val state = RawOrder.State(
        actualAmountS = order.actual.amountS,
        actualAmountB = order.actual.amountB,
        actualAmountFee = order.actual.amountFee,
        outstandingAmountS = order.outstanding.amountS,
        outstandingAmountB = order.outstanding.amountB,
        outstandingAmountFee = order.outstanding.amountFee,
        status = order.status
      )

      for {
        //需要更新到数据库
        //TODO(yongfeng): 暂时添加接口，需要永丰根据目前的使用优化dal的接口
        _ <- dbModule.orderService.updateOrderState(order.id, state)
        _ = if (trackOrderUpdated) {
          chainReorgManagerActor ! reorg.RecordOrderUpdateReq(
            order.block,
            Seq(order.id)
          )
        }
        _ <- order.status match {
          case STATUS_NEW | //
              STATUS_PENDING | //
              STATUS_PARTIALLY_FILLED =>
            log.debug(s"submitting order id=${order.id} to MMA")
            val order_ = order.copy(_reserved = None, _outstanding = None)
            for {
              matchRes <- (marketManagerActor ? SubmitSimpleOrder(
                order = Some(order_)
              )).mapAs[MarketManager.MatchResult]

              _ = matchRes.taker.status match {
                case STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES =>
                  self ! CancelOrder.Req(
                    matchRes.taker.id,
                    owner,
                    matchRes.taker.status,
                    Some(MarketPair(order.tokenS, order.tokenB))
                  )
                case _ =>
              }
            } yield Unit

          case STATUS_EXPIRED | //
              STATUS_DUST_ORDER | //
              STATUS_COMPLETELY_FILLED | //
              STATUS_SOFT_CANCELLED_BY_USER | //
              STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR | //
              STATUS_ONCHAIN_CANCELLED_BY_USER | //
              STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR | //
              STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES | //
              STATUS_SOFT_CANCELLED_LOW_BALANCE | //
              STATUS_SOFT_CANCELLED_DUPLICIATE =>
            log.debug(s"cancelling order id=${order.id} status=${order.status}")
            val marketPair = MarketPair(order.tokenS, order.tokenB)
            marketManagerActor ? CancelOrder.Req(
              id = order.id,
              marketPair = Some(marketPair)
            )

          case status =>
            throw ErrorException(
              ERR_INVALID_ORDER_DATA,
              s"unexpected order status: $status in: $order"
            )
        }

//        //TODO(yadong) 确认一下是不是要发送RawOrder，提交的订单是不是调用该方法
        rawOrder <- dbModule.orderDal.getOrder(order.id)
        _ = socketIONotifierActor ! rawOrder.get
      } yield Unit
    }
  }

  implicit val updatedAccountsProcessor = new UpdatedAccountsProcessor {

    def processUpdatedAccount(
        block: Long,
        address: String,
        tokenAddress: String
      ) = Future {
      chainReorgManagerActor ! reorg
        .RecordAccountUpdateReq(block, address, tokenAddress)
    }
  }

}
