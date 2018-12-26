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
import org.loopring.lightcone.ethereum.RawOrderValidatorImpl
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto.{
  XCancelOrderReq,
  XErrorCode,
  XSubmitOrderReq
}

object OrderHandlerMessageValidator{
  val name = "order_handler_message_validator"
}

class OrderHandlerMessageValidator()(implicit val config: Config)
    extends MessageValidator {

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
          rawOrder
      }

    case req @ XCancelOrderReq(_, owner, _, marketId) ⇒
      supportedMarkets.assertmarketIdIsValid(marketId)
      req.copy(
        owner = normalizeAddress(owner)
      )
  }
}
