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
import org.loopring.lightcone.core._

class AmountConverterSpec extends CommonSpec {

  val GTO = "GTO"
  val GTO_TOKEN = XTokenMetadata(GTO, 7, 0.2, 1400.0)
  implicit val tmm = new TokenMetadataManager()
  tmm.addToken(GTO_TOKEN)

  "AmountConverter.rawToDisplay" should "convert between raw and displayable amounts" in {
    val c = AmountConverter(GTO)

    c.rawToDisplay(BigInt("12300" + "0000000")) should be(12300.0)
    c.rawToDisplay(BigInt("12300")) should be(0.00123)

    c.rawToDisplay(BigInt("-12300" + "0000000")) should be(-12300.0)
    c.rawToDisplay(BigInt("-12300")) should be(-0.00123)

    c.rawToDisplay(BigInt("12300"), 6) should be(0.00123)
    c.rawToDisplay(BigInt("12300"), 5) should be(0.00123)
    c.rawToDisplay(BigInt("12300"), 4) should be(0.0012)
    c.rawToDisplay(BigInt("12300"), 3) should be(0.001)
    c.rawToDisplay(BigInt("12300"), 2) should be(0)
    c.rawToDisplay(BigInt("12300"), 1) should be(0)
    c.rawToDisplay(BigInt("12300"), 0) should be(0)

    c.rawToDisplay(BigInt("55555"), 6) should be(0.005556)
    c.rawToDisplay(BigInt("55555"), 5) should be(0.00556)
    c.rawToDisplay(BigInt("55555"), 4) should be(0.0056)
    c.rawToDisplay(BigInt("55555"), 3) should be(0.006)
    c.rawToDisplay(BigInt("55555"), 2) should be(0.01)
    c.rawToDisplay(BigInt("55555"), 1) should be(0)
    c.rawToDisplay(BigInt("55555"), 0) should be(0)

    c.rawToDisplay(BigInt("0000000")) should be(0)

    c.displayToRaw(12300.0) should be(BigInt("12300" + "0000000"))
    c.displayToRaw(0.00123) should be(BigInt("12300"))

    c.displayToRaw(-12300.0) should be(BigInt("-12300" + "0000000"))
    c.displayToRaw(-0.00123) should be(BigInt("-12300"))

    c.displayToRaw(0) should be(BigInt(0))
  }
}
