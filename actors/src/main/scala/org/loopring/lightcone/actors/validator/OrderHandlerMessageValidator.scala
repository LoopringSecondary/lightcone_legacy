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
import org.loopring.lightcone.actors.core.MultiAccountManagerActor
import org.loopring.lightcone.ethereum.RawOrderValidatorImpl
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{
  ErrorException,
  MarketHashProvider,
  SystemTimeProvider
}
import org.loopring.lightcone.proto._

object OrderHandlerMessageValidator {
  val name = "order_handler_message_validator"
}

class OrderHandlerMessageValidator()(implicit val config: Config)
    extends MessageValidator {
  val timeProvider = new SystemTimeProvider()
  val supportedMarkets = SupportedMarkets(config)

  private def normalizeAddress(address: String): String =
    try {
      Address(address).toString
    } catch {
      case _: Throwable ⇒
        throw ErrorException(
          XErrorCode.ERR_ETHEREUM_ILLEGAL_ADDRESS,
          message = s"invalid ethereum address:$address"
        )
    }

  override def validate: PartialFunction[Any, Any] = {

    case _ @XSubmitOrderReq(Some(order)) ⇒
      RawOrderValidatorImpl.validate(order) match {
        case Left(errorCode) ⇒
          throw ErrorException(
            errorCode,
            message = s"invalid order in XSubmitOrderReq:$order"
          )
        case Right(rawOrder) ⇒
          val multiAccountConfig =
            config.getConfig(MultiAccountManagerActor.name)
          val numOfShards = multiAccountConfig.getInt("num-of-shards")
          val now = timeProvider.getTimeMillis
          val state = XRawOrder.State(
            createdAt = now,
            updatedAt = now,
            status = XOrderStatus.STATUS_NEW
          )
          val marketHash =
            MarketHashProvider.convert2Hex(rawOrder.tokenS, rawOrder.tokenB)
          rawOrder.copy(
            state = Some(state),
            marketHash = marketHash,
            marketHashId = marketHash.hashCode, //TODO du: marketHash.hashCode ?
            addressShardId = MultiAccountManagerActor
              .getEntityId(order.owner, numOfShards)
              .toInt
          )
      }

    case req @ XCancelOrderReq(_, owner, _, marketId) ⇒
      supportedMarkets.assertmarketIdIsValid(marketId)
      req.copy(
        owner = normalizeAddress(owner)
      )
  }
}
