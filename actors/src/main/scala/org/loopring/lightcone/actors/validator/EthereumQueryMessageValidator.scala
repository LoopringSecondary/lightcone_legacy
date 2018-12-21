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
import org.loopring.lightcone.proto._

object EthereumQueryMessageValidator {
  val name = "ethereum_query_validator"
}

final class EthereumQueryMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  // Throws exception if validation fails.
  def validate = {
    case req: XGetBalanceAndAllowancesReq =>
      req
        .copy(tokens = req.tokens.map(Address.normalizeAddress))
        .copy(address = Address.normalizeAddress(req.address))

    case req: XGetBalanceReq =>
      req
        .copy(tokens = req.tokens.map(Address.normalizeAddress))
        .copy(address = Address.normalizeAddress(req.address))

    case req: XGetAllowanceReq =>
      req
        .copy(tokens = req.tokens.map(Address.normalizeAddress))
        .copy(address = Address.normalizeAddress(req.address))

    // case req: GetFilledAmountReq =>
  }
}
