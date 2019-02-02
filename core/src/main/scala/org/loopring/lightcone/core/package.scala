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

package org.loopring.lightcone

import org.loopring.lightcone.proto.MarketPair
import org.loopring.lightcone.core.base.MarketHash
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.core.data._
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import spire.math.Rational

package object core {

  // implicit classes
  implicit class _RichDouble(raw: Double) extends implicits.RichDouble(raw)
  implicit class _RichBigInt(raw: BigInt) extends implicits.RichBigInt(raw)

  implicit class _RichMatchable(raw: Matchable)
      extends implicits.RichMatchable(raw)
  implicit class _RichMarketPair(raw: MarketPair)
      extends implicits.RichMarketPair(raw)

  // implicit methods
  implicit def rational2BigInt(r: Rational) = r.toBigInt
}
