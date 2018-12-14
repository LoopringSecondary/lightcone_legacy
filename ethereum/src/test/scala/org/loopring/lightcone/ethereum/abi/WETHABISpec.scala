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
import org.web3j.utils.Numeric

class WETHABISpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val weth = WETHABI()

  override def beforeAll(): Unit = {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "encodeDepositFunction" should "encode class Parms of deposit function to  input" in {
    val parms = DepositFunction.Parms()
    val input = weth.deposit.pack(parms)
    info(input)
    input should be("0xd0e30db0")
  }

  "encodeWithdrawFunction" should "encode class Parms of withdraw function to  input" in {
    val parms = WithdrawFunction.Parms(wad = BigInt("29558242000000000000000"))
    val input = weth.withdraw.pack(parms)
    info(input)
    input should be(
      "0x2e1a7d4d0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    )
  }

  "decodeDepositEvent" should "decode event data and assemble to class Deposit" in {
    val data =
      "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000"
    val topics = Array(
      "0xe1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109c",
      "0x00000000000000000000000033debb5ee65549ffa71116957da6db17a9d8fe57"
    )
    val depositOpt = weth.depositEvent.unpack(data, topics)
    info(depositOpt.toString)
    depositOpt match {
      case None =>
      case Some(deposit) =>
        deposit.dst should be("0x33debb5ee65549ffa71116957da6db17a9d8fe57")
        deposit.wad.toString() should be("1000000000000000000")
    }
  }

  "decodeWithdrawFunction" should "decode event data and assemble to class Withdraw" in {
    val data =
      "0x2e1a7d4d0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val withdrawOpt = weth.withdraw.unpackInput(data)
    info(withdrawOpt.toString)
    withdrawOpt match {
      case None =>
      case Some(withdraw) =>
        withdraw.wad.toString() should be("29558242000000000000000")
    }
  }

  "decodeEventOfDeposit" should "decode event data and assemble to class Deposit" in {
    val data =
      "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000"
    val topics = Array(
      "0xe1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109c",
      "0x00000000000000000000000033debb5ee65549ffa71116957da6db17a9d8fe57"
    )
    val eventOpt = weth.unpackEvent(data, topics)
    info(eventOpt.toString)
    eventOpt match {
      case None =>
      case Some(deposit: DepositEvent.Result) =>
        deposit.dst should be("0x33debb5ee65549ffa71116957da6db17a9d8fe57")
        deposit.wad.toString() should be("1000000000000000000")
    }
  }

  "decodeFunctionOfWithdrawal" should "decode event data and assemble to class Withdraw" in {
    val data =
      "0x2e1a7d4d0000000000000000000000000000000000000000000006425b02acb8d7bd0000"
    val withdrawOpt = weth.unpackFunctionInput(data)
    info(withdrawOpt.toString)
    withdrawOpt match {
      case None =>
      case Some(withdraw: WithdrawFunction.Parms) =>
        withdraw.wad.toString() should be("29558242000000000000000")
    }
  }
}
