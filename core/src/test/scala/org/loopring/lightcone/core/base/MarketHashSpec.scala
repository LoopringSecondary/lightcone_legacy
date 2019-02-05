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

package org.loopring.lightcone.core

import org.loopring.lightcone.core.CommonSpec
import org.scalatest._

class MarketHashSpec extends CommonSpec {

  "marketHash" must "calculate a market hash by two address" in {
    val address1 = "0x50689da538c80f32f46fb224af5d9d06c3309633"
    val address2 = "0x6d0643f40c625a46d4ede0b11031b0907bc197d1"
    val marketHash1 = MarketHash(MarketPair(address1, address2)).toString
    val marketHash2 = MarketHash(MarketPair(address2, address1)).toString
    val t = MarketHash(MarketPair(address1, address2)).toString
    marketHash1 should be(marketHash2)
    t should be("0x3d6ede5134aa557420825295bf6c2d96b8f101e2")
  }
}
