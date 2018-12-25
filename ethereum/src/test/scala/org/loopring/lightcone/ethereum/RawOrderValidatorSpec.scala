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

package org.loopring.lightcone.ethereum

import scala.util.Properties
import org.scalatest._
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto._

class RawOrderValidatorSpec extends FlatSpec with Matchers {
  val validator: RawOrderValidator = RawOrderValidatorImpl

  "RawOrderValidator" should "be able to get hash of an order" in {
    val wethAddress = "0x3B39f10dC98b3fcd86a6d4837ff2BdF410710B94"
    val lrcAddress = "0x5eADE4Cbac9ecd6082Bb2A375185e2F8FCaeeb7F"
    val validSince = 1545619108

    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val walletAddr = dualAuthAddr
    val order1Owner = "0xFDa769A839DA57D88320E683cD20075f8f525a57"

    val params1 = (new XRawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withWallet(walletAddr)

    val feeParams1 = (new XRawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(
        ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16))
      )
      .withTokenRecipient(order1Owner)
      .withWalletSplitPercentage(10)

    val order1 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16)))
      .withAmountB(ByteString.copyFromUtf8(BigInt("1" + "0" * 21).toString(16)))
      .withValidSince(validSince)
      .withParams(params1)
      .withFeeParams(feeParams1)

    val hash = validator.calculateOrderHash(order1)
    val hash1Expected =
      "0x2fdae35faa777de91856debc80d213f0d1454e643ae644b562307ae1f47beef3"
    assert(hash == hash1Expected, "hash calculating method get wrong result.")
  }

  "RawOrderValidator" should "be able to validate is an order is valid" in {}
}
