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
  "user" should "be able to get hash of an order" in {
    // val lrcAddress = TestConfig.envOrElseConfig("contracts.LRC")
    // val wethAddress = TestConfig.envOrElseConfig("contracts.WETH")
    // val gtoAddress = TestConfig.envOrElseConfig("contracts.GTO")

    // val order1Owner = TestConfig.envOrElseConfig("accounts.a1.addr")
    // val order2Owner = TestConfig.envOrElseConfig("accounts.a2.addr")

    val miner = "0x23a51c5f860527f971d0587d130c64536256040d"
    val minerPrivKey = "0xa99a8d27d06380565d1cf6c71974e7707a81676c4e7cb3dad2c43babbdca2d23"
    val transactionOrigin = "0xc0ff3f78529ab90f765406f7234ce0f2b1ed69ee"
    val minerFeeRecipient = "0x611db73454c27e07281d2317aa088f9918321415"

    val wethAddress = "0x3B39f10dC98b3fcd86a6d4837ff2BdF410710B94"
    val lrcAddress = "0x5eADE4Cbac9ecd6082Bb2A375185e2F8FCaeeb7F"
    val validSince = 1545288649

    val dualAuthAddr = "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA"
    val walletAddr = dualAuthAddr
    val order1Owner = "0xFDa769A839DA57D88320E683cD20075f8f525a57"
    val order2Owner = "0xf5B3ab72F6E80d79202dBD37400447c11618f21f"

    val validator: RawOrderValidator = new RawOrderValidatorImpl
    val context: XRingBatchContext = (new XRingBatchContext)
      .withMiner(miner)
      .withMinerPrivateKey(minerPrivKey)
      .withFeeRecipient(minerFeeRecipient)
      .withTransactionOrigin(transactionOrigin)
      .withLrcAddress(lrcAddress)
    val generator: RingBatchGenerator = new RingBatchGeneratorImpl(context)

    val mockOrderSig1 = "0x01411bdfe8ac29b828887bc17e926e1938585eef3edd535d53efad605726663fd124e737c1837300ff9afca20ba60f19d19daa98fe357b1719ebcf765943e99ca46047"
    val mockOrderSig2 = "0x00411bdfe8ac29b828887bc17e926e1938585eef3edd535d53efad605726663fd124e737c1837300ff9afca20ba60f19d19daa98fe357b1719ebcf765943e99ca46028"
    val mockDualAuthSig = "0x00411bdfe8ac29b828887bc17e926e1938585eef3edd535d53efad605726663fd124e737c1837300ff9afca20ba60f19d19daa98fe357b1719ebcf765943e99ca46047"

    val params1 = (new XRawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withWallet(walletAddr)
      .withSig(mockOrderSig1)
      .withDualAuthSig(mockDualAuthSig)

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
      .withValidSince(validSince)
      .withParams(params1)
      .withFeeParams(feeParams1)

    val order1WithDefault = validator.setupEmptyFieldsWithDefaults(order1, lrcAddress)

    val hash = validator.calculateOrderHash(order1WithDefault)
    println(s"order1 hash:$hash")
    val hash1Expected = "0xa078d272fe15177fec76268c3896319e8736853583b67d5bf746001b987f43d0"
    // assert(hash == hash1Expected, "hash1 not as expected ")

    val validateResult = validator.validate(order1WithDefault)

    val params2 = (new XRawOrder.Params)
      .withDualAuthAddr(dualAuthAddr)
      .withWallet(walletAddr)
      .withSig(mockOrderSig2)
      .withDualAuthSig(mockDualAuthSig)

    val feeParams2 = (new XRawOrder.FeeParams)
      .withTokenFee(lrcAddress)
      .withAmountFee(ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16)))
      .withTokenRecipient(order2Owner)
      .withWalletSplitPercentage(20)

    val order2 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order2Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFromUtf8(BigInt("1" + "0" * 21).toString(16)))
      .withAmountB(ByteString.copyFromUtf8(BigInt("1" + "0" * 18).toString(16)))
      .withValidSince(validSince)
      .withParams(params2)
      .withFeeParams(feeParams2)

    val order2WithDefault = validator.setupEmptyFieldsWithDefaults(order2, lrcAddress)

    val hash2 = validator.calculateOrderHash(order2WithDefault)
    println(s"order2 hash:$hash2")

    val hash2Expected = "0x65d3a688a5f0d0dad84bee5d5ec8dbc540748dd2f95583ecdfcfe3cada333dbe"
    // assert(hash2 == hash2Expected, "hash2 not as expected ")

    val validateResult2 = validator.validate(order2WithDefault)
    println(s"validateResult: $validateResult2")

    val xRingBatch = generator.generateAndSignRingBatch(Seq(Seq(order1, order2)))
    println(s"xRingBatch: $xRingBatch")
    println(s"xRingBatch hash:${xRingBatch.hash}")

    val paramStr = generator.toSubmitableParamStr(xRingBatch)
    println(s"paramStr:$paramStr")

    val expectedParamStr = "0x00000002000100030008000d00120000002b00300035003a0042004a00000001004b00000000004b00000050006900000000003a0000000000000000000a00000000000000000000000000000082003500300042003a004a00020002004b00000000004b00000087006900000000003a00000000000000000014000000000000000000000000020001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000007B5DAAa252Ce94Bc295FC2945D3ED818Fc4284Ff582e0c3B8fD6d876dCA070B59d713F11A5D7cC4F000000000000000000000000000000000000000000000000000000000000004300411b8dee1fc45edb51c4097a251317b551222a8aafda20a23520d5331b271f1037254122c27b49cdd5dffb865895c639a7793218e9be6355cba8bc6453b7116c25a400B1193050202731e5ceE09783A3Bc2982DD4D96F27B73396C7E985F1253e3542033FeeAe8d86e45fF7332238fBdC0eB9Afc2433db3AC8F1fFd7609eAe0000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000003635c9adc5dea000005c1892a625d683699a8aAf07a6b4120C845202532FdB1016000000000000000000000000000000000000000000000000000000000000004300411c7adb1759e54fe545025a342c5774dad2baa7d9ab9c29280b1946f7198d1a8ce871fd196619a477dd7c6511e7349932f0347451f81c4202a6cf999d7675b17dd300000000000000000000000000000000000000000000000000000000000000004300411b79636545742b6bd4865fba349b20d7afccd805bc2f187eef338f14da3c57b1d21f4373eca4ba1d2539fc6ebf483e5423d0180c1f967babbd95daf1c4f9ebb43300248CAe44997944C7D29ef0ee835F61fEd35Aca63000000000000000000000000000000000000000000000000000000000000004301411b195e1759512fbb4a53e080ad743b4344291233a7d81762d52275af51d6de359434f47a42b73b63601d345e04035c9c1a471161f2d1a264471f4d36871819ae3300"

    assert(paramStr == expectedParamStr, "invalid paramstr")
  }
}
