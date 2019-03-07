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

package io.lightcone.relayer.implicits

import io.lightcone.core.{Currency, ErrorCode, ErrorException}
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.external._

private[relayer] class RichCurrency(currency: Currency) {

  def getAddress() = {
    if (currency.isEth) {
      Address.ZERO.toString
    } else {
      // NumericConversion.toHexString(BigInt(Math.abs(currency.name.hashCode)))
      ""
    }
  }

  def getSlug() = {
    if (QUOTE_TOKEN.contains(currency.name)) {
      currency match {
        case Currency.ETH => "ethereum"
        case Currency.BTC => "bitcoin"
        case m =>
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not support currency:$m"
          )
      }
    } else {
      s"loopring-${currency.name.toLowerCase}"
    }
  }
}
