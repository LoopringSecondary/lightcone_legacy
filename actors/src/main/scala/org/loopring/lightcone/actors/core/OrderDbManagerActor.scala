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

import akka.actor._
import akka.util.Timeout
import org.loopring.lightcone.auxiliary.model.{ PaginationQuery, _ }
import org.loopring.lightcone.auxiliary.order.OrderAccessorImpl
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.auxiliary.{ XMarketSide, XOrderSaveResult, XOrderType }
import akka.pattern.pipe

import scala.concurrent.ExecutionContext

object OrderDbManagerActor {
  val name = "order_db_manager"
}

class OrderDbManagerActor()(
  implicit
  ec: ExecutionContext,
  timeout: Timeout,
  accessor: OrderAccessorImpl
) extends Actor
  with ActorLogging {

  def receive: Receive = {
    case XRawOrderQryByHashReq(hash) ⇒
      log.info(s"get order by hash : ${hash}")
      accessor.getOrderByHash(hash) pipeTo sender

    case XRawOrderQryByPagingReq(reqOption, pagingOption) ⇒

      log.info(s"query order by query: ${reqOption}, and paging : ${pagingOption}")

      val orderQuery = reqOption.map { req ⇒
        OrderQuery(req.statuses, req.owner, req.market, req.hashes, Some(req.side), Some(req.orderType))
      }

      val paginationQuery = pagingOption.map { paging ⇒
        PaginationQuery(paging.offset, paging.limit)
      }

      accessor.pageQueryOrders(orderQuery, paginationQuery) pipeTo sender

    case XRawOrderSoftCancelReq(hash, cutoffTime, market, cancelType, owner) ⇒

      log.info(s"soft cancel order by hash: ${hash}, cutoffTime: ${cutoffTime}, market: " +
        s"${market}, cancelType: ${cancelType}, owner: ${owner}")

      val cancelOrder = CancelOrderOption(hash, cutoffTime, market, cancelType, owner)
      accessor.softCancelOrders(Some(cancelOrder)) pipeTo sender

    case XRawOrderSaveOrUpdateReq(Some(xorder),
    updatedBlock,
    dealtAmountS,
    dealtAmountB,
    cancelledAmountS,
    cancelledAmountB,
    status,
    broadcastTime,
    powNonce,
    market,
    side,
    price,
    orderType) ⇒

      // TODO (litao) 下面的字段应该是从其他渠道传入
      val order = new Order(
        rawOrder = toRawOrder(xorder),
        updatedBlock = updatedBlock,
        dealtAmountS = dealtAmountS,
        dealtAmountB = dealtAmountB,
        cancelledAmountS = cancelledAmountS,
        cancelledAmountB = cancelledAmountB,
        status = status,
        broadcastTime = broadcastTime,
        powNonce = powNonce,
        market = market,
        side = side,
        price = price,
        orderType = orderType
      )

      accessor.saveOrder(order).foreach {
        case XOrderSaveResult.SUBMIT_SUCCESS ⇒
          log.info("save or update order success")
          log.info("todo: sender message to AMA or MMA")
        case result ⇒
          log.error(s"save or update order failed, result : ${result}")
      }

  }

  private def toRawOrderEssential: PartialFunction[XRawOrder, RawOrderEssential] = {
    case xorder: XRawOrder ⇒
      RawOrderEssential(
        owner = xorder.owner,
        tokenS = xorder.tokenS,
        tokenB = xorder.tokenB,
        amountS = xorder.amountS.toString("UTF-8"),
        amountB = xorder.amountB.toString("UTF-8"),
        validSince = xorder.validSince,
        dualAuthAddress = xorder.dualAuthAddress,
        broker = xorder.broker,
        orderInterceptor = xorder.orderInterceptor,
        wallet = xorder.wallet,
        validUntil = xorder.validUntil,
        allOrNone = xorder.allOrNone,
        feeToken = xorder.feeToken,
        feeAmount = xorder.feeAmount.toString("UTF-8"),
        feePercentage = xorder.feePercentage,
        tokenSFeePercentage = xorder.tokenSFeePercentage,
        tokenBFeePercentage = xorder.tokenBFeePercentage,
        tokenRecipient = xorder.tokenRecipient,
        walletSplitPercentage = xorder.walletSplitPercentage,
        hash = xorder.hash
      )
  }

  private def toRawOrder: PartialFunction[XRawOrder, RawOrder] = {
    case xorder: XRawOrder ⇒
      RawOrder(
        rawOrderEssential = toRawOrderEssential(xorder),
        version = xorder.version,
        tokenSpendableS = xorder.tokenSpendableS.toString("UTF-8"),
        tokenSpendableFee = xorder.tokenSpendableFee.toString("UTF-8"),
        brokerSpendableS = xorder.brokerSpendableS.toString("UTF-8"),
        brokerSpendableFee = xorder.brokerSpendableFee.toString("UTF-8"),
        sig = xorder.sig,
        dualAuthSig = xorder.dualAuthSig,
        waiveFeePercentage = xorder.waiveFeePercentage,
        dualPrivateKey = xorder.dualPrivateKey
      )
  }

}
