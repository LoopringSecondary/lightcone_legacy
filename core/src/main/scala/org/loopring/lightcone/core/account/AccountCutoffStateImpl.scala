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

package org.loopring.lightcone.core.account

import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.core.base.MarketKey
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.collection.mutable.Map

class AccountCutoffStateImpl()(implicit timeProvider: TimeProvider)
    extends AccountCutoffState {
  private val marketPairCutoffs = Map.empty[String, Long]
  private var ownerCutoff: Long = -1L

  def setTradingPairCutoff(
      marketKey: String,
      cutoff: Long
    ) = {
    if (!(cutoff <= timeProvider.getTimeSeconds())) {
      marketPairCutoffs.get(marketKey) match {
        case None => marketPairCutoffs.put(marketKey, cutoff)
        case Some(c) =>
          if (c < cutoff)
            marketPairCutoffs.put(marketKey, cutoff)
      }
    }
  }

  def setCutoff(cutoff: Long) = {
    if (!(cutoff <= timeProvider.getTimeSeconds()))
      if (ownerCutoff < cutoff) ownerCutoff = cutoff
  }

  def isOrderCutoffByOwner(rawOrder: RawOrder) =
    ownerCutoff >= rawOrder.validSince

  def isOrderCutoffByTradingPair(rawOrder: RawOrder) = {
    val marketKey = MarketKey(rawOrder.tokenS, rawOrder.tokenB).toString
    marketPairCutoffs.contains(marketKey) &&
    marketPairCutoffs(marketKey) >= rawOrder.validSince
  }
}
