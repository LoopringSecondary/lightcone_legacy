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
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.lib._

object MultiAccountManagerMessageValidator {
  val name = "multi_account_manager_validator"
}

// This class can be deleted in the future.
final class MultiAccountManagerMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  // Throws exception if validation fails.
  private def verifyAddressValid(address: String) = {
    throw ErrorException(ERR_INVALID_ARGUMENT, s"invalid address $address")
  }

  def validate = {
    case req: XCancelOrderReq ⇒
      verifyAddressValid(req.owner)
      req

    case req: XSubmitOrderReq ⇒ req
    case req: XRecoverOrderReq => req
    case req: XGetBalanceAndAllowancesReq ⇒ req
    case req: XAddressBalanceUpdated ⇒ req
    case req: XAddressAllowanceUpdated ⇒ req
  }
}
