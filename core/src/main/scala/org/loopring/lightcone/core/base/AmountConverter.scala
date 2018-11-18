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

package org.loopring.lightcone.core.base

import org.loopring.lightcone.core.data.Rational

object AmountConverter {
  def apply(token: String)(implicit tmm: TokenMetadataManager) =
    new AmountConverter(token)
}

class AmountConverter(
    token: String
)(implicit tmm: TokenMetadataManager) {
  assert(tmm.hasToken(token))
  lazy val meta = tmm.getToken(token).get
  lazy val scaling = Rational(Math.pow(10, meta.decimals))

  def rawToDisplay(amount: BigInt): Double = (Rational(amount) / scaling).doubleValue
  def rawToDisplay(amount: BigInt, precision: Int): Double = {
    BigDecimal(rawToDisplay(amount: BigInt))
      .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
      .doubleValue
  }

  def displayToRaw(amount: Double): BigInt = (Rational(amount) * scaling).bigintValue
}
