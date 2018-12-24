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

import org.scalatest._
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto._
import org.web3j.crypto._
import org.web3j.utils.Numeric

class RingBatchGeneratorSpec extends FlatSpec with Matchers {
  val miner = "0x23a51c5f860527f971d0587d130c64536256040d"

  val minerPrivKey =
    "0xa99a8d27d06380565d1cf6c71974e7707a81676c4e7cb3dad2c43babbdca2d23"
  val transactionOrigin = "0xc0ff3f78529ab90f765406f7234ce0f2b1ed69ee"
  val minerFeeRecipient = "0x611db73454c27e07281d2317aa088f9918321415"

  val lrcAddress = TestConfig.envOrElseConfig("contracts.LRC")
  val wethAddress = TestConfig.envOrElseConfig("contracts.WETH")
  val gtoAddress = TestConfig.envOrElseConfig("contracts.GTO")

  implicit val context: XRingBatchContext = XRingBatchContext()
    .withMiner(miner)
    .withMinerPrivateKey(minerPrivKey)
    .withFeeRecipient(minerFeeRecipient)
    .withTransactionOrigin(transactionOrigin)
    .withLrcAddress(lrcAddress)
  // println(s"xRingBatchContext: $xRingBatchContext")

  // "sign message" should "be the same as web3" in {
  //   val credentials = Credentials.create(context.minerPrivateKey)
  //   val hash =
  //     "0x997975290ca7006e12221705eedba70148b415fc94c89b280650f91c8a351fac"
  //   val sigData = Sign.signPrefixedMessage(
  //     Numeric.hexStringToByteArray(hash),
  //     credentials.getEcKeyPair
  //   )

  //   val sStr = sigData.getS.map("%02x" format _).mkString
  //   val rStr = sigData.getR.map("%02x" format _).mkString

  //   println(s"sig: $rStr$sStr")

  // }

  "simple 2 tradable orders" should "be able to generate a ring" in {
    val order1Owner = TestConfig.envOrElseConfig("accounts.a1.addr")
    val order2Owner = TestConfig.envOrElseConfig("accounts.a2.addr")

    val ringBatchGenerator = RingBatchGeneratorImpl

    val order1 = new XRawOrder()
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFromUtf8(1000e18.toLong.toHexString))
      .withAmountB(ByteString.copyFromUtf8(1e18.toLong.toHexString))

    val order2 = new XRawOrder()
      .withVersion(0)
      .withOwner(order2Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFromUtf8(1e18.toLong.toHexString))
      .withAmountB(ByteString.copyFromUtf8(1000e18.toLong.toHexString))

    val orders = Seq(Seq(order1, order2))
    val xRingBatch = ringBatchGenerator.generateAndSignRingBatch(orders)
    println(s"xRingBatch: $xRingBatch")

    val param = ringBatchGenerator.toSubmitableParamStr(xRingBatch)
    println(s"param str: $param")

  }
}
