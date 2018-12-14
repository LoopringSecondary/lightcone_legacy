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

package org.loopring.lightcone.ethereum.abi

import org.scalatest._

class BurnRateTableAbiSpec
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  val burnRateTableAbi = BurnRateTableAbi()

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "encodeBURN_BASE_PERCENTAGEFunction" should "encode BURN_BASE_PERCENTAGEFunction Params to input" in {
    val input = burnRateTableAbi.burn_BASE_PERCENTAGE.pack(
      BURN_BASE_PERCENTAGEFunction.Params()
    )
    input should be("0xfed4dd1e")
  }

  "decodeBURN_BASE_PERCENTAGEFunctionResult" should "decode result of eth_call to BURN_BASE_PERCENTAGEFunction Result" in {
    val resp =
      "0x00000000000000000000000000000000000000000000000000000000000003e8"
    val result = burnRateTableAbi.burn_BASE_PERCENTAGE.unpackResult(resp)
    result match {
      case Some(res) ⇒ res.burnRate.toString should be("1000")
      case _ ⇒
    }
  }

  "encodeGetBurnRateFunction" should "encode GetBurnRateFunction Params to input" in {
    val token = "0x6bfceb2cb021ec87cb2525811f5b7d4834037f62"
    val params = GetBurnRateFunction.Params(token)
    val input = burnRateTableAbi.getBurnRate.pack(params)
    input should be(
      "0x42b5f3750000000000000000000000006bfceb2cb021ec87cb2525811f5b7d4834037f62"
    )
  }

  "decodeGetBurnRateFunction" should "decode eth_call result to  GetBurnRateFunction Result" in {
    val resp =
      "0x0000000000000000000000000000000000000000000000000000000000050032"
    val result = burnRateTableAbi.getBurnRate.unpackResult(resp)
    result.map { res ⇒
      res.burnRate.toString should be("327730")
    }
  }

  "decodeTokenTierUpgradedEvent" should "decode data to tokenTierUpgradedEvent Result" in {
    val data =
      "0x00000000000000000000000000000000000000000000152d02c7e14af6800000"
    val topics = Seq(
      "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      "0x00000000000000000000000091e658c123e23f509201a99f30574f6548724639",
      "0x0000000000000000000000000000000000000000000000000000000000000000"
    )
    val result =
      burnRateTableAbi.tokenTierUpgradedEvent.unpack(data, topics.toArray)

    info(result.toString)
    result.map { res ⇒
      res.add should be("0x91e658c123e23f509201a99f30574f6548724639")
      res.tier.toString() should be("100000000000000000000000")
    }
  }

}
