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
import org.loopring.lightcone.ethereum.data.Address

object EthereumQueryMessageValidator {
  val name = "ethereum_query_validator"
}

final class EthereumQueryMessageValidator()(implicit val config: Config)
    extends MessageValidator {

  // TODO(yadong): 我们不仅要判断Jan地址是不是合法的，害怕把地址改写成规范的模式
  // This method should throw exception for an invalid address
  private def normalizeAddress(address: String): String = address

  def validate = {
    case req: XGetBalanceAndAllowancesReq =>
      req
        .copy(tokens = req.tokens.map(normalizeAddress))
        .copy(address = normalizeAddress(req.address))

    case req: XGetBalanceReq =>
      req
        .copy(tokens = req.tokens.map(normalizeAddress))
        .copy(address = normalizeAddress(req.address))

    case req: XGetAllowanceReq =>
      req
        .copy(tokens = req.tokens.map(normalizeAddress))
        .copy(address = normalizeAddress(req.address))

    // case req: GetFilledAmountReq =>
  }
}
