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

package io.lightcone.core

import com.google.inject.Inject
import com.google.inject.name.Named

class DustOrderEvaluator @Inject()(
    implicit
    @Named("dust-order-threshold") threshold: Double = 0.0,
    tve: TokenValueEvaluator) {

  def isOriginalDust(order: Matchable) =
    isDust(order.tokenS, order.original.amountS)

  def isOutstandingDust(order: Matchable) =
    isDust(order.tokenS, order.outstanding.amountS)

  def isActualDust(order: Matchable) =
    isDust(order.tokenS, order.actual.amountS)

  def isMatchableDust(order: Matchable) =
    isDust(order.tokenS, order.matchable.amountS)

  private def isDust(
      tokenS: String,
      amountS: BigInt
    ): Boolean = {
    println(s"threshold:$threshold")
    tve.getValue(tokenS, amountS) < threshold
  }
}
