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

package org.loopring.lightcone.core.data

import org.loopring.lightcone.proto.MarketId

abstract class Cutoff(ttl1: Long) {
  val ttl: Long = ttl1
}

case class MarketPairCutoff(
    marketId: MarketId,
    cutoff: Long)
    extends Cutoff(cutoff)

case class OwnerCutoff(
    owner: String,
    cutoff: Long)
    extends Cutoff(cutoff)

case class OrderCutoff(
    orderHash: String,
    validUntil: Long)
    extends Cutoff(validUntil) {}
