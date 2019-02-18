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

package io.lightcone.core.testing

import io.lightcone.core._
import io.lightcone.lib._

trait Constants {

  private val rand = new scala.util.Random(31)

  val LRC = Address("0xEF68e7C694F40c8202821eDF525dE3782458639f").toString
  val GTO = Address("0xC5bBaE50781Be1669306b9e001EFF57a2957b09d").toString
  val DAI = Address("0x27594c42A4c7584F00C98894435Ab9a2482e882C").toString
  val WETH = Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2").toString

  val TOKENS = Seq(LRC, GTO, DAI, WETH)

  def randomToken() = {
    TOKENS(rand.nextInt(TOKENS.size))
  }

  object Addr {
    def apply() = rand.alphanumeric.take(22).mkString("")
    def apply(idx: Int) = addresses(idx)

    val addresses = Seq(
      "0x76f30f4bb5D71377cAc22f6ddc39C7b3AfE6E377",
      "0xf74d556cbB95b7BCCDad8284bc68110C74c91E7B",
      "0x0f8aA39A58ADcc3Df98d826Ac798aB837CC0833C"
    )
  }

  val metadataManager: MetadataManager = new AbstractMetadataManager {
    val defaultBurnRateForMarket: Double = 0.2
    val defaultBurnRateForP2P: Double = 0.2

    // Initalize this
    tokenAddressMap = Map.empty[String, Token]
    tokenSymbolMap = Map.empty[String, Token]
    marketMap = Map.empty[String, MarketMetadata]
  }

}
