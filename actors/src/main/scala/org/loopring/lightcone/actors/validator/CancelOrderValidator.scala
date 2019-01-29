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

package org.loopring.lightcone.actors.validator

import com.typesafe.config.Config
import org.loopring.lightcone.core.base.MetadataManager
import org.loopring.lightcone.ethereum.ethereum._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils._

import scala.concurrent.ExecutionContext

final class CancelOrderValidator(
    implicit
    val config: Config,
    timeProvider: TimeProvider,
    ec: ExecutionContext,
    metadataManager: MetadataManager,
    dbModule: DatabaseModule)
    extends MessageValidator {

  override def validate: PartialFunction[Any, Any] = {
    case req: CancelOrder.Req =>
      for {
        orderOpt <- dbModule.orderService.getOrder(req.id)
        order = orderOpt.getOrElse(
          throw ErrorException(ErrorCode.ERR_ORDER_NOT_EXIST)
        )
        marketId = MarketId(order.tokenS, order.tokenB)
        _ = metadataManager.assertMarketIdIsActive(marketId)
      } yield {
        if (!checkSign(req))
          throw ErrorException(
            ErrorCode.ERR_ORDER_VALIDATION_INVALID_SIG,
            s"not authorized to cancel this order $req.id"
          )
        req.copy(
          owner = Address.normalizeAddress(req.owner),
          status = OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
          marketId = Some(
            marketId.copy(
              baseToken = marketId.baseToken.toLowerCase(),
              quoteToken = marketId.quoteToken.toLowerCase()
            )
          )
        )
      }
    case _ => throw ErrorException(ErrorCode.ERR_INVALID_ARGUMENT)
  }

  //TODO:针对具体什么签名还未确定，目前只有单个订单，采用订单的签名简单测试
  private def checkSign(req: CancelOrder.Req): Boolean = {
    val sigBytes = Numeric.hexStringToByteArray(req.sig)
    val v = sigBytes(2)
    val r = sigBytes.slice(3, 35)
    val s = sigBytes.slice(35, 67)
    verifyEthereumSignature(
      Numeric.hexStringToByteArray(req.id),
      r,
      s,
      v,
      Address(req.owner)
    )
  }
}
