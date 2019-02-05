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

import org.scalatest._
import com.google.protobuf.ByteString
import io.lightcone.proto._
import io.lightcone.core._
import org.web3j.crypto._
import org.web3j.utils.Numeric

class RingBatchGeneratorSpec extends FlatSpec with Matchers {
  "generateAndSignRingBatch" should "be able to generate a ring from order seqs" in {
    val miner = "0x23a51c5f860527f971d0587d130c64536256040d"

    val minerPrivKey =
      "0xa99a8d27d06380565d1cf6c71974e7707a81676c4e7cb3dad2c43babbdca2d23"
    val transactionOrigin = "0xc0ff3f78529ab90f765406f7234ce0f2b1ed69ee"
    val minerFeeRecipient = "0x611db73454c27e07281d2317aa088f9918321415"

    val lrcAddress = TestConfig.envOrElseConfig("contracts.LRC")
    val wethAddress = TestConfig.envOrElseConfig("contracts.WETH")
    val gtoAddress = TestConfig.envOrElseConfig("contracts.GTO")

    val order1Owner = TestConfig.envOrElseConfig("accounts.a1.addr")
    val order2Owner = TestConfig.envOrElseConfig("accounts.a2.addr")

    implicit val context: RingBatchContext = RingBatchContext()
      .withMiner(miner)
      .withMinerPrivateKey(minerPrivKey)
      .withFeeRecipient(minerFeeRecipient)
      .withTransactionOrigin(transactionOrigin)
      .withLrcAddress(lrcAddress)
    val ringBatchGenerator = Protocol2RingBatchGenerator

    val order1 = new RawOrder()
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFrom(BigInt(1000e18.toLong).toByteArray))
      .withAmountB(ByteString.copyFrom(BigInt(1e18.toLong).toByteArray))

    val order2 = new RawOrder()
      .withVersion(0)
      .withOwner(order2Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFrom(BigInt(1e18.toLong).toByteArray))
      .withAmountB(ByteString.copyFrom(BigInt(1000e18.toLong).toByteArray))

    val orders = Seq(Seq(order1, order2))
    val xRingBatch: RingBatch =
      ringBatchGenerator.generateAndSignRingBatch(orders)
  }

  "toSubmitableParamStr" should "be able to serialize a RingBatch object to param string" in {
    val miner = "0x23a51c5f860527f971d0587d130c64536256040d"
    val minerPrivKey =
      "0xa99a8d27d06380565d1cf6c71974e7707a81676c4e7cb3dad2c43babbdca2d23"
    val transactionOrigin = "0xc0ff3f78529ab90f765406f7234ce0f2b1ed69ee"
    val minerFeeRecipient = "0x611db73454c27e07281d2317aa088f9918321415"

    val wethAddress = "0x3B39f10dC98b3fcd86a6d4837ff2BdF410710B94"
    val lrcAddress = "0x5eADE4Cbac9ecd6082Bb2A375185e2F8FCaeeb7F"
    val validSince = 1545619108

    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val dualAuthPrivateKey =
      "0x2cebf2be8c8542bc9ab08f8bfd6e5cbd77b7ce3ba30d99bea19887ef4b24f08c"
    val walletAddr = dualAuthAddr
    val order1Owner = "0xFDa769A839DA57D88320E683cD20075f8f525a57"
    val order2Owner = "0xf5B3ab72F6E80d79202dBD37400447c11618f21f"

    val validator: RawOrderValidator = RawOrderValidatorDefault
    val generator: RingBatchGenerator = Protocol2RingBatchGenerator
    implicit val context: RingBatchContext = RingBatchContext()
      .withMiner(miner)
      .withMinerPrivateKey(minerPrivKey)
      .withFeeRecipient(minerFeeRecipient)
      .withTransactionOrigin(transactionOrigin)
      .withLrcAddress(lrcAddress)

    val orderSig1 =
      "0x00411b60253172658e0f1de6277245d4d1c8dd379259554b646c41513ecdfe0b19a205048ac90d2df4c6b61f321d2cc20a31b08e16e081724bec57a72db3c573db3dd4"
    val orderSig2 =
      "0x01411c37034ce977b6b9ba41af6cac3cb36256d58129ebed9127dbeec8844b570a601e326e641b7800b2b0e965a01dcb07564090923a4f2f8ff61f3276417c0c17a61e"

    val params1 = (new RawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withDualAuthPrivateKey(dualAuthPrivateKey)
      .withWallet(walletAddr)
      .withSig(orderSig1)

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
      .withValidSince(1545619109)
      .withParams(params2)
      .withFeeParams(feeParams2)

    val xRingBatch =
      generator.generateAndSignRingBatch(Seq(Seq(order1, order2)))
    // println(s"xRingBatch: $xRingBatch")
    // println(s"xRingBatch hash:${xRingBatch.hash}")
    // println(s"ringBatch sig: ${xRingBatch.sig}")

    val paramStr = generator.toSubmitableParamStr(xRingBatch)
    // println(s"paramStr:$paramStr")

    val expectedParamStr =
      "0x00000002000100030008000d00120000002b00300035003a0042004a00000001004b00000000004b00000050006900000000003a0000000000000000000a00000000000000000000000000000082003500300042003a008700020002004b00000000004b00000088006900000000003a0000000000000000001400000000000000000000000002000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000611db73454c27e07281d2317aa088f991832141523a51c5f860527f971d0587d130c64536256040d000000000000000000000000000000000000000000000000000000000000004300411c3385bd1aa7110949f76e790a685e690fd24af09d109f49eead506529bf212ec07d68d2c03b2ed86d14a3609488b247b4be94972da781bf293dc0a18edfe1638600fda769a839da57d88320e683cd20075f8f525a573b39f10dc98b3fcd86a6d4837ff2bdf410710b945eade4cbac9ecd6082bb2a375185e2f8fcaeeb7f0000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000003635c9adc5dea000005c2046a466d3444ad66fc32abcec9b38a4181066b1146cca000000000000000000000000000000000000000000000000000000000000004300411b60253172658e0f1de6277245d4d1c8dd379259554b646c41513ecdfe0b19a205048ac90d2df4c6b61f321d2cc20a31b08e16e081724bec57a72db3c573db3dd400000000000000000000000000000000000000000000000000000000000000004300411b7ea4758921b4a8cc6dca98f35abaa8c08ef0fc64162a9bd9046060f4b221971f051b6f7536c653857b4ea4aa483d8e2199c0b71cbbe3ce616a777d3e43b2dff800f5b3ab72f6e80d79202dbd37400447c11618f21f5c2046a5000000000000000000000000000000000000000000000000000000000000004301411c37034ce977b6b9ba41af6cac3cb36256d58129ebed9127dbeec8844b570a601e326e641b7800b2b0e965a01dcb07564090923a4f2f8ff61f3276417c0c17a61e00"

    assert(paramStr == expectedParamStr, "generate wrong paramstr")
  }
}
