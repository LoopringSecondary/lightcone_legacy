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

package org.loopring.lightcone.auxiliary.order

import com.google.inject.Inject
import com.google.protobuf.ByteString
import org.loopring.lightcone.auxiliary.model._
import org.loopring.lightcone.proto.actors.XRawOrderQryByPagingReq.XRawOrderPaging
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.auxiliary.XOrderSaveResult
import org.loopring.lightcone.proto.core._
import org.slf4s.Logging

import scala.concurrent.{ ExecutionContext, Future }

class OrderDatabaseManagerImpl @Inject() (
    orderAccessor: OrderAccessor,
    orderWriteHelper: OrderWriteHelper
)(
    implicit
    ex: ExecutionContext
) extends OrderDatabaseManager with Logging {

  implicit def str2ByteString(str: String): ByteString = ByteString.copyFrom(str, "UTF-8")

  implicit def byteString2Str(bs: ByteString): String = bs.toString("UTF-8")

  override def validateOrder(xraworder: XRawOrder): Either[XErrorCode, XRawOrder] = {

    // TODO(litao, xiaolu) validateOrder 返回值需要修改, 这里需要一个错误类型
    orderWriteHelper.validateOrder(toOrder(xraworder)) match {
      case ValidateResult(true, _, _) ⇒ Right(xraworder)
      case ValidateResult(false, errorCode, reason) ⇒
        log.error(s"xraworder is invalid, reason: ${reason}")
        Left(XErrorCode.fromName(errorCode).getOrElse(XErrorCode.ERR_UNKNOWN))
    }

  }

  override def saveOrUpdate(xraworder: XRawOrder): Future[Option[XRawOrder]] = {

    // TODO(litao) 这里的返回值我修改为 Future[Option[XRawOrder]]
    orderAccessor.saveOrder(toOrder(xraworder)).map {
      case XOrderSaveResult.SUBMIT_SUCCESS ⇒ Some(xraworder)
      case _ ⇒
        log.error("save order failed")
        None
    }

  }

  override def getOrderByHash(orderHash: String): Future[Option[XRawOrder]] = {
    orderAccessor.getOrderByHash(orderHash).map(_.map(toXRawOrder))
  }

  private def toRawOrderEssential: PartialFunction[XRawOrder, RawOrderEssential] = {
    case xorder: XRawOrder ⇒
      RawOrderEssential(
        owner = xorder.owner,
        tokenS = xorder.tokenS,
        tokenB = xorder.tokenB,
        amountS = xorder.amountS,
        amountB = xorder.amountB,
        validSince = xorder.validSince,
        dualAuthAddress = xorder.dualAuthAddress,
        broker = xorder.broker,
        orderInterceptor = xorder.orderInterceptor,
        wallet = xorder.wallet,
        validUntil = xorder.validUntil,
        allOrNone = xorder.allOrNone,
        feeToken = xorder.feeToken,
        feeAmount = xorder.feeAmount,
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
        tokenSpendableS = xorder.tokenSpendableS,
        tokenSpendableFee = xorder.tokenSpendableFee,
        brokerSpendableS = xorder.brokerSpendableS,
        brokerSpendableFee = xorder.brokerSpendableFee,
        sig = xorder.sig,
        dualAuthSig = xorder.dualAuthSig,
        waiveFeePercentage = xorder.waiveFeePercentage,
        dualPrivateKey = xorder.dualPrivateKey
      )
  }

  private def toOrder: PartialFunction[XRawOrder, Order] = {
    case xorder: XRawOrder ⇒
      Order(
        rawOrder = toRawOrder(xorder),
        updatedBlock = xorder.updatedBlock,
        dealtAmountS = xorder.dealtAmountS,
        dealtAmountB = xorder.dealtAmountB,
        cancelledAmountS = xorder.cancelledAmountS,
        cancelledAmountB = xorder.cancelledAmountB,
        status = xorder.status,
        broadcastTime = xorder.broadcastTime,
        powNonce = xorder.powNonce,
        market = xorder.market,
        side = xorder.side,
        price = xorder.price,
        orderType = xorder.orderType
      )
  }

  private def toXRawOrder: PartialFunction[Order, XRawOrder] = {
    case order: Order ⇒
      XRawOrder(
        owner = order.rawOrder.rawOrderEssential.owner,
        tokenS = order.rawOrder.rawOrderEssential.tokenS,
        tokenB = order.rawOrder.rawOrderEssential.tokenB,
        amountS = order.rawOrder.rawOrderEssential.amountS,
        amountB = order.rawOrder.rawOrderEssential.amountB,
        validSince = order.rawOrder.rawOrderEssential.validSince,
        allOrNone = order.rawOrder.rawOrderEssential.allOrNone,
        feeToken = order.rawOrder.rawOrderEssential.feeToken,
        feeAmount = order.rawOrder.rawOrderEssential.feeAmount,
        sig = order.rawOrder.sig,
        dualAuthSig = order.rawOrder.dualAuthSig,
        hash = order.rawOrder.rawOrderEssential.hash,
        validUntil = order.rawOrder.rawOrderEssential.validUntil,
        wallet = order.rawOrder.rawOrderEssential.wallet,
        dualAuthAddress = order.rawOrder.rawOrderEssential.dualAuthAddress,
        broker = order.rawOrder.rawOrderEssential.broker,
        orderInterceptor = order.rawOrder.rawOrderEssential.orderInterceptor,
        version = order.rawOrder.version,
        walletSplitPercentage = order.rawOrder.rawOrderEssential.walletSplitPercentage,
        tokenSFeePercentage = order.rawOrder.rawOrderEssential.tokenSFeePercentage,
        tokenBFeePercentage = order.rawOrder.rawOrderEssential.tokenBFeePercentage,
        waiveFeePercentage = order.rawOrder.waiveFeePercentage,
        feePercentage = order.rawOrder.rawOrderEssential.feePercentage,
        tokenSpendableS = order.rawOrder.tokenSpendableS,
        tokenSpendableFee = order.rawOrder.tokenSpendableFee,
        brokerSpendableS = order.rawOrder.brokerSpendableS,
        brokerSpendableFee = order.rawOrder.brokerSpendableFee,
        tokenRecipient = order.rawOrder.rawOrderEssential.tokenRecipient,
        dualPrivateKey = order.rawOrder.dualPrivateKey,

        updatedBlock = order.updatedBlock,
        dealtAmountS = order.dealtAmountS,
        dealtAmountB = order.dealtAmountB,
        cancelledAmountS = order.cancelledAmountS,
        cancelledAmountB = order.cancelledAmountB,
        status = order.status,
        broadcastTime = order.broadcastTime,
        powNonce = order.powNonce,
        market = order.market,
        side = order.side,
        price = order.price,
        orderType = order.orderType
      )
  }

}
