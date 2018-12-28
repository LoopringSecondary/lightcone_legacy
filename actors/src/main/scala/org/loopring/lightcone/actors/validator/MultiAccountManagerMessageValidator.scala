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
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto._

object MultiAccountManagerMessageValidator {
  val name = "multi_account_manager_validator"
}

// This class can be deleted in the future.
final class MultiAccountManagerMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  val supportedMarkets = SupportedMarkets(config)

  def validate = {
    case req: CancelOrderReq ⇒
      req.copy(owner = Address.normalizeAddress(req.owner))

    case req: SubmitSimpleOrderReq ⇒
      req.order match {
        case None =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"bad request:${req}"
          )
        case Some(order) =>
          supportedMarkets.assertmarketIdIsValid(
            MarketId(order.tokenS, order.tokenB)
          )
          req.copy(
            order = Some(
              order.copy(
                tokenB = Address.normalizeAddress(order.tokenB),
                tokenS = Address.normalizeAddress(order.tokenS),
                tokenFee = Address.normalizeAddress(order.tokenFee)
              )
            ),
            owner = Address.normalizeAddress(req.owner)
          )
      }
    case req: Recover.RecoverOrderReq => req
    case req: GetBalanceAndAllowancesReq ⇒
      req.copy(
        address = Address.normalizeAddress(req.address),
        tokens = req.tokens.map(Address.normalizeAddress)
      )

    case req: AddressBalanceUpdated ⇒
      req.copy(
        address = Address.normalizeAddress(req.address),
        token = Address.normalizeAddress(req.token)
      )

    case req: AddressAllowanceUpdated ⇒
      req.copy(
        address = Address.normalizeAddress(req.address),
        token = Address.normalizeAddress(req.token)
      )

  }
}
