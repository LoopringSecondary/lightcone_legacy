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

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._

// TODO(dongw): we need a price provider
class TokenValueEstimator()(implicit tm: TokenManager) {

  def getEstimatedValue(
      tokenAddr: String,
      amount: BigInt
    ): Double = {
    if (amount.signum <= 0) 0
    else if (!tm.hasToken(tokenAddr)) 0
    else {
      val token = tm.getToken(tokenAddr)
      (Rational(token.fromWei(amount)) *
        Rational(token.meta.currentPrice)).doubleValue
    }
  }

}
