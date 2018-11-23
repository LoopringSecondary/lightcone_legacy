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

package org.loopring.lightcone.lib.data

import org.scalatest._

class RingSerializeSpec extends FlatSpec with Matchers {

  val one = BigInt("1000000000000000000")

  "serialize" should "generate ring input data" in {
    info("[sbt lib/'testOnly *RingDataSpec -- -z serialize']")

    val originRingData = "0x00000002000100030008000d00120000002b00300035003a0042004a00000001004b00000000005000000055006e00000000003a0000000000000000000a00000087003500300042003a004a00020002008c0000000000500000009100aa00000000003a0000000000000000001402000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000d5cff27c2ce7a8a394a2b7ed3cdb6bcdbdd0daaa7b4ead52dc80b2479da192d887caf41b49089ccf000000000000000000000000000000000000000000000000000000000000004300411cb704a01418304f503a6a0d084f08573d66c2c4cd47fe9b4e9ef11fc09968d8a03fd24d8bb8ab40210251b2605273fa191976bb51de3d1e1cf0085df1e3a2cf19006feaf3926489df7b21b49d591fd556f4fc912d7e5d238e455ac40d1feab3e089bd8ee30964d3b399731a4a5c148c5a8623fe72b3c5205492ebd4a3a40000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000003635c9adc5dea000005beea35858df38c79f2d9e8ced060c5221a5ce4f034c6c31cdc65a2beb0df0ff92890170a2129d1078a9943e000000000000000000000000000000000000000000000000000000000000004300411cd4d3b99f505f862b1614daa7c37e308ac513deed026249e05bc34428d533672040c02a65109c332e93d92b170d3486965e18b4977b659f4bd9499f851d83d31e00000000000000000000000000000000000000000000000000000000000000004300411c8fe1813e7d18e5fba0e148ea802359935a7d888275827ea3d3ceb38fc555703e7fa60fce5d9b77836705528524157960fae56271527f092e37918c422301de3900795bc214bdd87d4d77804dd7046a327bec3a3afe869c0b8e546c35e6d01c7348c4a5a9ecf69fdf64000000000000000000000000000000000000000000000000000000000000004301411bbf82517bc459df770bd293ae00eed29fcd5195d4cea3ae10427bbdbe65afd9a1377459ad0afee7ae73f1e82b59f0d75045351969ea8e433cee7503c3fa771dbd00000000000000000000000000000000000000000000000000000000000000004300411bbe70850ad7a98f8341a1aec6f74cf364cc3660fa219fb272209d1e98540db99f7c571d20047dc94af05356717af19da8a5f2e193d8481a077dd11fad5307b52000"
    val originRingHash = "0x8a875cef3203824a5f6e3f6671dbf286f656d78e203260e5d71d633005be7819"
    val originOrderHash1 = "0x2946b79a3ce8e87ede8884ed358d419a6e8702f35f3fe14cfbcc3bc093fb3ec2"
    val originOrderHash2 = "0xeec980a852d5bfee306fa0bd2d0d1db4b2100d7400c9259aeb7d455094c365b9"

    // 变化
    val token1 = "0x5d238e455ac40d1feab3e089bd8ee30964d3b399"
    val token2 = "0x731a4a5c148c5a8623fe72b3c5205492ebd4a3a4"
    val feetoken = token2

    val sig1 = "0x00411cd4d3b99f505f862b1614daa7c37e308ac513deed026249e05bc34428d533672040c02a65109c332e93d92b170d3486965e18b4977b659f4bd9499f851d83d31e"
    val dualAuthSig1 = "0x00411c8fe1813e7d18e5fba0e148ea802359935a7d888275827ea3d3ceb38fc555703e7fa60fce5d9b77836705528524157960fae56271527f092e37918c422301de39"
    val validSince1 = 1542366040

    val sig2 = "0x01411bbf82517bc459df770bd293ae00eed29fcd5195d4cea3ae10427bbdbe65afd9a1377459ad0afee7ae73f1e82b59f0d75045351969ea8e433cee7503c3fa771dbd"
    val dualAuthSig2 = "0x00411bbe70850ad7a98f8341a1aec6f74cf364cc3660fa219fb272209d1e98540db99f7c571d20047dc94af05356717af19da8a5f2e193d8481a077dd11fad5307b520"
    val validSince2 = 1542366040

    val minerSig = "0x00411cb704a01418304f503a6a0d084f08573d66c2c4cd47fe9b4e9ef11fc09968d8a03fd24d8bb8ab40210251b2605273fa191976bb51de3d1e1cf0085df1e3a2cf19"

    // 固定不变
    val wallet = "0xcdc65a2beb0df0ff92890170a2129d1078a9943e"
    val owner1 = "0x6feaf3926489df7b21b49d591fd556f4fc912d7e"
    val owner2 = "0x795bc214bdd87d4d77804dd7046a327bec3a3afe"
    val dualAuth1 = "0x58df38c79f2d9e8ced060c5221a5ce4f034c6c31"
    val dualAuth2 = "0x869c0b8e546c35e6d01c7348c4a5a9ecf69fdf64"
    val tokenRecipient1 = "0x6feaf3926489df7b21b49d591fd556f4fc912d7e"
    val tokenRecipient2 = "0x795bc214bdd87d4d77804dd7046a327bec3a3afe"

    val miner = "0x7b4ead52dc80b2479da192d887caf41b49089ccf"
    val feeRecipient = "0xd5cff27c2ce7a8a394a2b7ed3cdb6bcdbdd0daaa"
    val transactionOrigin = "0xbfc112d62f9083f456a9da18f2fd24454f583642"

    val generator: RingSerializer = new RingSerializerImpl(feetoken)
    val deserializer: RingDeserializer = new RingDeserializerImpl(feetoken)

    val raworder1 = Order(
      tokenS = token1,
      tokenB = token2,
      amountS = BigInt("1000000000000000000"),
      amountB = BigInt(1000) * one,
      owner = owner1,
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = dualAuth1,
      allOrNone = false,
      validSince = validSince1,
      wallet = wallet,
      walletSplitPercentage = 10,
      tokenReceipt = tokenRecipient1,
      feeToken = feetoken,
      tokenSpendableFee = 1,
      sig = sig1,
      dualAuthSig = dualAuthSig1
    )
    val order1 = raworder1.copy(hash = raworder1.cryptoHash)

    val raworder2 = Order(
      tokenS = token2,
      tokenB = token1,
      amountS = BigInt(1000) * one,
      amountB = BigInt("1000000000000000000"),
      owner = owner2,
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = dualAuth2,
      allOrNone = false,
      validSince = validSince2,
      wallet = wallet,
      walletSplitPercentage = 20,
      tokenReceipt = tokenRecipient2,
      feeToken = feetoken,
      sig = sig2,
      dualAuthSig = dualAuthSig2,
      tokenSpendableS = 2,
      tokenSpendableFee = 2
    )
    val order2 = raworder2.copy(hash = raworder2.cryptoHash)

    val ring = Ring(
      orders = Seq(order1, order2),
      miner = miner,
      feeReceipt = feeRecipient,
      transactionOrigin = transactionOrigin,
      sig = minerSig,
      ringOrderIndex = Seq(Seq(0, 1))
    )
    val ringhash = ring.cryptoHash

    order1.hash should be(originOrderHash1)
    order2.hash should be(originOrderHash2)
    ringhash should be(originRingHash)

    val serializeRes = generator.serialize(ring)
    serializeRes should be(originRingData)
  }
}
