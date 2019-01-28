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
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._

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
        req.copy(
          owner = Address.normalizeAddress(req.owner),
          status = OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
          marketId = Some(
            marketId.copy(
              primary = marketId.primary.toLowerCase(),
              secondary = marketId.secondary.toLowerCase()
            )
          )
        )
      }
    case _ => throw ErrorException(ErrorCode.ERR_INVALID_ARGUMENT)
  }

  //TODO:impl it
  private def checkSign(req: CancelOrder.Req): Boolean = {
    true
  }
}
