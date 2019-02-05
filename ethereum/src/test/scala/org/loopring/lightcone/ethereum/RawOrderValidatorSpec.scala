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

package io.lightcone.ethereum

import scala.util.Properties
import org.scalatest._
import com.google.protobuf.ByteString
import io.lightcone.core._
import io.lightcone.proto._

class RawOrderValidatorSpec extends FlatSpec with Matchers {
  import ErrorCode._
  val validator: RawOrderValidator = RawOrderValidatorDefault

  "calculateOrderHash" should "be able to get hash of an order" in {
    val wethAddress = "0x3B39f10dC98b3fcd86a6d4837ff2BdF410710B94"
    val lrcAddress = "0x5eADE4Cbac9ecd6082Bb2A375185e2F8FCaeeb7F"
    val validSince = 1545619108

    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val walletAddr = dualAuthAddr
    val order1Owner = "0xFDa769A839DA57D88320E683cD20075f8f525a57"

    val params1 = (new RawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withWallet(walletAddr)

    val feeParams1 = (new RawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withWalletSplitPercentage(10)

    val order1 = (new RawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withAmountB(ByteString.copyFrom(BigInt("1" + "0" * 21).toByteArray))
      .withValidSince(validSince)
      .withParams(params1)
      .withFeeParams(feeParams1)

    val hash = validator.calculateOrderHash(order1)
    val hash1Expected =
      "0x2fdae35faa777de91856debc80d213f0d1454e643ae644b562307ae1f47beef3"
    assert(hash == hash1Expected, "hash calculating method get wrong result.")
  }

  "validate" should "be able to validate if an order is valid or not" in {
    val wethAddress = "0x3B39f10dC98b3fcd86a6d4837ff2BdF410710B94"
    val lrcAddress = "0x5eADE4Cbac9ecd6082Bb2A375185e2F8FCaeeb7F"
    val validSince = 1545619108

    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val walletAddr = dualAuthAddr
    val order1Owner = "0xFDa769A839DA57D88320E683cD20075f8f525a57"
    val dualAuthPrivateKey =
      "0x2cebf2be8c8542bc9ab08f8bfd6e5cbd77b7ce3ba30d99bea19887ef4b24f08c"

    val params1 = (new RawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withWallet(walletAddr)

    val feeParams1 = (new RawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withTokenRecipient(order1Owner)
      .withWalletSplitPercentage(10)

    val order1 = (new RawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withAmountB(ByteString.copyFrom(BigInt("1" + "0" * 21).toByteArray))
      .withValidSince(validSince)
      .withParams(params1)
      .withFeeParams(feeParams1)

    val validateRes1 = validator.validate(order1)
    val expectedRes1 =
      Left(ERR_ORDER_VALIDATION_INVALID_MISSING_DUALAUTH_PRIV_KEY)
    assert(validateRes1 == expectedRes1, "validate order not as expected.")

    val params2 = params1.withDualAuthPrivateKey(dualAuthPrivateKey)
    val order2 = order1.withParams(params2)
    val validateRes2 = validator.validate(order2)
    val expectedRes2 = Left(ERR_ORDER_VALIDATION_INVALID_SIG)
    assert(validateRes2 == expectedRes2, "validate order not as expected.")

    val params3 = params2.withSig(
      "0x00411b60253172658e0f1de6277245d4d1c8dd379259554b646c41513ecdfe0b19a205048ac90d2df4c6b61f321d2cc20a31b08e16e081724bec57a72db3c573db3dd4"
    )
    val order3 = order2.withParams(params3)
    val validateRes3 = validator.validate(order3)
    val expectedRes3 = Right(order3)
    assert(validateRes3 == expectedRes3, "validate order not as expected.")
  }

  it should "be able to verify order's signature(eip712)" in {
    val wethAddress = "0x1090B9813DF54d1F03dB5596939b69DC89ba1Be4"
    val lrcAddress = "0x703C3606e05E151DCe6ECBCd9998617C30dfeFfd"
    val order2Owner = "0xf5B3ab72F6E80d79202dBD37400447c11618f21f"
    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val dualAuthPrivateKey =
      "0x2cebf2be8c8542bc9ab08f8bfd6e5cbd77b7ce3ba30d99bea19887ef4b24f08c"
    val walletAddr = dualAuthAddr

    val orderSig2 =
      "0x01411b448f2be050fb924c688077ec0167c6f374e2392cd870366be0c05944a08a9578280c72d522724741a7b6b7a487d01499a2604032cdd6e2c630b5978898511534"

    val params2 = (new RawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withDualAuthPrivateKey(dualAuthPrivateKey)
      .withWallet(walletAddr)
      .withSig(orderSig2)

    val feeParams2 = (new RawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withTokenRecipient(order2Owner)
      .withWalletSplitPercentage(20)

    val order2 = (new RawOrder)
      .withVersion(0)
      .withOwner(order2Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFrom(BigInt("1" + "0" * 21).toByteArray))
      .withAmountB(ByteString.copyFrom(BigInt("1" + "0" * 18).toByteArray))
      .withValidSince(1545813653)
      .withParams(params2)
      .withFeeParams(feeParams2)

    val validateRes = validator.validate(order2)
    val expectedRes = Right(order2)
    assert(validateRes == expectedRes, "validate order not as expected.")
  }
}
