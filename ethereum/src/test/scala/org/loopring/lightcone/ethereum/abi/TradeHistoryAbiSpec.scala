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

package io.lightcone.ethereum.abi

import org.web3j.utils.Numeric
import org.scalatest._

class TradeHistoryAbiSpec
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  val tradeHistoryAbi = TradeHistoryAbi()

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "encodeFilledFunction" should "encode params to input" in {
    val orderHash = Numeric.hexStringToByteArray("0x123")
    val input = tradeHistoryAbi.filled.pack(FilledFunction.Params(orderHash))
    input should be(
      "0x288cdc910000000000000000000000000000000000000000000000000000000000000123"
    )
  }

  "decodeFillFunctionResult" should "decode eth_call data to  Fill Function Result" in {
    val data =
      "0x0000000000000000000000000000000000000000000000000000000000000123"
    val resultOpt = tradeHistoryAbi.filled.unpackResult(data)
    resultOpt match {
      case None =>
      case Some(res) =>
        res.amount.toString should be("291")
    }
  }

}
