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

import java.util.{Timer, TimerTask}

import org.loopring.lightcone.core.data.{
  Cutoff,
  MarketPairCutoff,
  OrderCutoff,
  OwnerCutoff
}
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto.RawOrder
import org.web3j.utils.Numeric

import scala.collection.mutable._

class AccountCutoffPoolImpl()(implicit val timeProvider: TimeProvider)
    extends AccountCutoffPool {

  private val marketPairCutoffs = Map.empty[BigInt, MarketPairCutoff]
  private var ownerCutoff: Option[OwnerCutoff] = None
  private val orderCutoffs = Map.empty[String, Long]

  def addCutoff(cutoff: MarketPairCutoff) = {
    if (!(cutoff.cutoff <= timeProvider.getTimeSeconds())) {
      val marketCode = Numeric.toBigInt(cutoff.marketId.primary) xor
        Numeric.toBigInt(cutoff.marketId.secondary)
      marketPairCutoffs.get(marketCode) match {
        case None => marketPairCutoffs.put(marketCode, cutoff)
        case Some(c) =>
          if (c.cutoff < cutoff.cutoff)
            marketPairCutoffs.put(marketCode, cutoff)
      }
    }
  }

  def addCutoff(cutoff: OwnerCutoff) = {
    if (!(cutoff.cutoff <= timeProvider.getTimeSeconds()))
      ownerCutoff match {
        case None    => ownerCutoff = Some(cutoff)
        case Some(c) => if (c.cutoff < cutoff.cutoff) ownerCutoff = Some(c)
      }
  }

  def addCutoff(cutoff: OrderCutoff) = {
    orderCutoffs.put(cutoff.orderHash, cutoff.validUntil)
    orderCutoffs.map { c =>
      if (c._2 <= timeProvider.getTimeSeconds()) {
        orderCutoffs.remove(c._1) //todo: 需要测试能否remove
      }
    }
  }

  def isOwnerCutOff(rawOrder: RawOrder): Boolean =
    ownerCutoff.nonEmpty && ownerCutoff.get.cutoff < rawOrder.validSince

  def isMarketPairCheck(rawOrder: RawOrder): Boolean = {
    val marketCode = Numeric.toBigInt(rawOrder.tokenS) xor Numeric.toBigInt(
      rawOrder.tokenB
    )
    marketPairCutoffs.contains(marketCode) && marketPairCutoffs(marketCode).cutoff < rawOrder.validSince
  }

  def isOrderCheck(rawOrder: RawOrder): Boolean = {
    orderCutoffs.contains(rawOrder.hash)
  }

  def isCutOff(rawOrder: RawOrder): Boolean = {
    return isOwnerCutOff(rawOrder) || isMarketPairCheck(rawOrder) || isOrderCheck(
      rawOrder
    )
  }
}
