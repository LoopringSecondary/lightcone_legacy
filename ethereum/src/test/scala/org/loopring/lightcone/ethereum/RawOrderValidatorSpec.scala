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

package org.loopring.lightcone.ethereum.data

import scala.util.Properties
import org.scalatest._
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto._

class RawOrderValidatorSpec extends FlatSpec with Matchers {
  val validator: RawOrderValidator = new RawOrderValidatorImpl

  "user" should "be able to get hash of an order" in {
    // val lrcAddress = TestConfig.envOrElseConfig("contracts.LRC")
    // val wethAddress = TestConfig.envOrElseConfig("contracts.WETH")
    // val gtoAddress = TestConfig.envOrElseConfig("contracts.GTO")

    // val order1Owner = TestConfig.envOrElseConfig("accounts.a1.addr")
    // val order2Owner = TestConfig.envOrElseConfig("accounts.a2.addr")

    val wethAddress = "0x9a8ccb389E9AAf81E889deA26CE58A855f085b6C"
    val lrcAddress = "0x98A82a4DC1ea681bFDB08A7952d6E590cfc177c9"

    val dualAuthAddr1 = "0x15d6D8a0cff888B9D3f6B2D916Dc2A19b9652310"
    val walletAddr1 = "0x6883818661dd47b0d6b3184AA781FE837f7c9335"

    val order1Owner = "0x94379bF0b21fdc5f40023177BC00F9eE8BB8bBc1"
    val order2Owner = ""

    val params1 = (new XRawOrder.Params)
      .withDualAuthAddr(dualAuthAddr1)
      .withWallet(walletAddr1)

    val feeParams1 = (new XRawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16)))
      .withTokenRecipient(order1Owner)
      .withWalletSplitPercentage(10)

    val order1 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16)))
      .withAmountB(ByteString.copyFromUtf8(BigInt("1" + "0" * 21).toString(16)))
      .withValidSince(1545049035)
      .withParams(params1)
      .withFeeParams(feeParams1)

    println(s"order1: $order1")

    val order1WithDefault = validator.setupEmptyFieldsWithDefaults(order1, lrcAddress)
    println(s"order1WithDefault: $order1WithDefault")

    val hash = validator.calculateOrderHash(order1WithDefault)
    println(s"hash:$hash")

    val validateResult = validator.validate(order1WithDefault)
    println(s"validateResult: $validateResult")
  }
}
