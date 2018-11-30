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

import org.scalatest._

// QUESTION(fukun): XXXSPec should be used to test XXX. Where is RingData class?
class RingDataSpec extends FlatSpec with Matchers {

  val one = BigInt("1000000000000000000")
  val lrcAddress = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44"
  val generator: RingSerializer = new RingSerializerImpl(lrcAddress)
  val deserializer: RingDeserializer = new RingDeserializerImpl(lrcAddress)

  "simpleTest1" should "serialize and deserialize" in {
    info("[sbt lib/'testOnly *RingDataSpec -- -z simpleTest1']")

    val originInput = "0x00000002000100030008000d00120000002b00300035003a0042004a00000001004b00000000005000000055006e00000000003a0000000000000000000a00000087003500300042003a008c00020002008d0000000000500000009200ab00000000003a0000000000000000001402000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000fff0e52e473c384a57e32b9394fba174b4849756cd48d3d60bd4f0c2c5d9188f6e4f4d5d6f0b0d39000000000000000000000000000000000000000000000000000000000000004300411cf101f8cc1f8f7e6bbbc3c6be043b33ceec09ae42976fc498505d3115b38705e63fb9e2e8ac08d3ee19d891cba845261a4079be141043156e13162abc498fb2b000e5f9d599e79acc52b2a2a5fa2e986525482528310d0e13874d2efe785583a6d8a0675f8e802d91293b243b0e87228aa330a56e0af3f2733f9c780b440000000000000000000000000000000000000000000000000de0b6b3a764000000000000000000000000000000000000000000000000003635c9adc5dea000005be93f2db345f56dd00e11a9e06d36dff4921c5e4e4cbddab5eb3fd0a6011cb1a47ddbde913744d544597d3e000000000000000000000000000000000000000000000000000000000000004300411b0a1a07ea138912127cd7701af73b9480296a6101d5acd6bf672678316ab230c339203e93470b68c0e3cae398554344216184fdd9196072038049abf856640e7c00000000000000000000000000000000000000000000000000000000000000004300411cd1d289a88d3c0e59a3fdcad6588e9488ed42c3b129e7c2bb170cf02bc2a901c1768e0c08dd6f04dda3d92324a4e4d76a721acef529b996d2b9a44b4024cf61b700b0b12b5ded3a6ae85f234bb60726f00b1ecd2ec15be93f2ea876ab5c19ff8c8065b1114cf7dfe9d09686afbf000000000000000000000000000000000000000000000000000000000000004300411be4ea0397bb23ff5b7f934f407df36b5ec57c0885eb31ae8667da0bcc6d88642059ad063269b65f4505ead30e7cfdbe8b932e6269017e41161dbca196de8ebffe00000000000000000000000000000000000000000000000000000000000000004300411c25e1eefc2800351331482972f1696a6db314c730ad37433386b92603758e8bc83063fe88d649915e9bc8b909d2f6e06e8f2701d2781bf2229fdbeffd6cdbe72a00"

    val raworder1 = Order(
      tokenS = "0x0d0e13874d2efe785583a6d8a0675f8e802d9129",
      tokenB = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      amountS = BigInt("1000000000000000000"),
      amountB = BigInt(1000) * one,
      owner = "0xe5f9d599e79acc52b2a2a5fa2e98652548252831",
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = "0xb345f56dd00e11a9e06d36dff4921c5e4e4cbdda",
      allOrNone = false,
      validSince = 1542012717,
      wallet = "0xb5eb3fd0a6011cb1a47ddbde913744d544597d3e",
      walletSplitPercentage = 10,
      tokenReceipt = "0xe5f9d599e79acc52b2a2a5fa2e98652548252831",
      feeToken = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      tokenSpendableFee = 1,
      sig = "0x00411b0a1a07ea138912127cd7701af73b9480296a6101d5acd6bf672678316ab230c339203e93470b68c0e3cae398554344216184fdd9196072038049abf856640e7c",
      dualAuthSig = "0x00411cd1d289a88d3c0e59a3fdcad6588e9488ed42c3b129e7c2bb170cf02bc2a901c1768e0c08dd6f04dda3d92324a4e4d76a721acef529b996d2b9a44b4024cf61b7"
    )
    val order1 = raworder1.copy(hash = raworder1.cryptoHash)

    val raworder2 = Order(
      tokenS = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      tokenB = "0x0d0e13874d2efe785583a6d8a0675f8e802d9129",
      amountS = BigInt(1000) * one,
      amountB = BigInt("1000000000000000000"),
      owner = "0xb0b12b5ded3a6ae85f234bb60726f00b1ecd2ec1",
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = "0xa876ab5c19ff8c8065b1114cf7dfe9d09686afbf",
      allOrNone = false,
      validSince = 1542012718,
      wallet = "0xb5eb3fd0a6011cb1a47ddbde913744d544597d3e",
      walletSplitPercentage = 20,
      tokenReceipt = "0xb0b12b5ded3a6ae85f234bb60726f00b1ecd2ec1",
      feeToken = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      sig = "0x00411be4ea0397bb23ff5b7f934f407df36b5ec57c0885eb31ae8667da0bcc6d88642059ad063269b65f4505ead30e7cfdbe8b932e6269017e41161dbca196de8ebffe",
      dualAuthSig = "0x00411c25e1eefc2800351331482972f1696a6db314c730ad37433386b92603758e8bc83063fe88d649915e9bc8b909d2f6e06e8f2701d2781bf2229fdbeffd6cdbe72a",
      tokenSpendableS = 2,
      tokenSpendableFee = 2
    )
    val order2 = raworder2.copy(hash = raworder2.cryptoHash)

    val ring = Ring(
      orders = Seq(order1, order2),
      miner = "0xcd48d3d60bd4f0c2c5d9188f6e4f4d5d6f0b0d39",
      feeReceipt = "0xfff0e52e473c384a57e32b9394fba174b4849756",
      transactionOrigin = "0x77ddd79b1c8345809b5b7f25cd0058d211471eb0",
      sig = "0x00411cf101f8cc1f8f7e6bbbc3c6be043b33ceec09ae42976fc498505d3115b38705e63fb9e2e8ac08d3ee19d891cba845261a4079be141043156e13162abc498fb2b0",
      ringOrderIndex = Seq(Seq(0, 1))
    )
    order1.hash should be("0xe9e12caed875fd92b46efbc4c413d56a796764adfd64a62ee532a5858676b7ef")
    order2.hash should be("0x8b164448f5a5839f301a616c7bfffaa684ab81210f0000e11641fbb088dead0c")

    val serializeRes = generator.serialize(ring)
    info("generated ring data: " + serializeRes)
    serializeRes should be(originInput)

    val deserializeRes = deserializer.deserialize(originInput)

    deserializeRes.orders.size should be(2)
    compare(deserializeRes.orders.head, order1) should be(true)
    compare(deserializeRes.orders.last, order2) should be(true)
  }

  "simpleTest2" should "serialize and deserialize" in {
    info("[sbt lib/'testOnly *RingDataSpec -- -z simpleTest2']")

    val originInput = "0x00000002000100040008000d00120000002b00300035003a0042004a00000001004b00000000005000000055006e00000000003a0000000000000000000a00000087003500300042003a008c00020003008d0000000000500000009200ab00000000003a0000000000000000001402000100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000fff0e52e473c384a57e32b9394fba174b4849756cd48d3d60bd4f0c2c5d9188f6e4f4d5d6f0b0d39000000000000000000000000000000000000000000000000000000000000004300411c6de0e2b3f39640d1d9d334c37d99d1caaaaacdb1f3748bef9abcf6a3f60186b26392784ed8bb7041e6ca0eb0faefdc99cd00925033c05d2223758ae82c1b039500e5f9d599e79acc52b2a2a5fa2e986525482528310d0e13874d2efe785583a6d8a0675f8e802d9129186fc679eefba90c7e37805debaffe9b6c2d0d7c0000000000000000000000000000000000000000000000000de0b6b3a76400000000000000000000000000000000000000000000000000a2a15d09519be000005be93f35b345f56dd00e11a9e06d36dff4921c5e4e4cbddab5eb3fd0a6011cb1a47ddbde913744d544597d3e000000000000000000000000000000000000000000000000000000000000004300411c106e9a8fbd5e6c3d28439667267bef0298e1baf199f01edbdaa0907f61dd7ba42ac8d9a3087ec762e83eb4f6e7163c41faa7fabeae5e12a1e26764785e4f55cd00000000000000000000000000000000000000000000000000000000000000004300411b210773ff4ba461287bcc618545ebfce5e42cb3a5a6d6bcda3287387ceeb574950b85d0bfd4ca1b10ac7c93da1ba221108e081b4fb994baaaf5ce275ddaba0cb700b0b12b5ded3a6ae85f234bb60726f00b1ecd2ec15be93f38a876ab5c19ff8c8065b1114cf7dfe9d09686afbf000000000000000000000000000000000000000000000000000000000000004300411ba54418d6f77e03c47461fdca3ec6cc5bbb65edc13d824f43015e2913b1fb84495b33d1071577795e3514ddd5e298abb1c2f1e6562c0c05587edd1063b26eb36e00000000000000000000000000000000000000000000000000000000000000004300411bbe5a02bef3248f86f78c7577fc6b5afb87d3d5e87b57a1e4e582c6ef347bc9ac1c50f9b76d83c4a19f1ead95a982d1ff914d5eaab67554d2df795e7ece86ca0700"

    val raworder1 = Order(
      tokenS = "0x0d0e13874d2efe785583a6d8a0675f8e802d9129",
      tokenB = "0x186fc679eefba90c7e37805debaffe9b6c2d0d7c",
      amountS = BigInt("1000000000000000000"),
      amountB = BigInt(1000) * one * 3,
      owner = "0xe5f9d599e79acc52b2a2a5fa2e98652548252831",
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = "0xb345f56dd00e11a9e06d36dff4921c5e4e4cbdda",
      allOrNone = false,
      validSince = 1542012725,
      wallet = "0xb5eb3fd0a6011cb1a47ddbde913744d544597d3e",
      walletSplitPercentage = 10,
      tokenReceipt = "0xe5f9d599e79acc52b2a2a5fa2e98652548252831",
      feeToken = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      tokenSpendableFee = 1,
      sig = "0x00411c106e9a8fbd5e6c3d28439667267bef0298e1baf199f01edbdaa0907f61dd7ba42ac8d9a3087ec762e83eb4f6e7163c41faa7fabeae5e12a1e26764785e4f55cd",
      dualAuthSig = "0x00411b210773ff4ba461287bcc618545ebfce5e42cb3a5a6d6bcda3287387ceeb574950b85d0bfd4ca1b10ac7c93da1ba221108e081b4fb994baaaf5ce275ddaba0cb7"
    )
    val order1 = raworder1.copy(hash = raworder1.cryptoHash)

    val raworder2 = Order(
      tokenS = "0x186fc679eefba90c7e37805debaffe9b6c2d0d7c",
      tokenB = "0x0d0e13874d2efe785583a6d8a0675f8e802d9129",
      amountS = BigInt(1000) * one * 3,
      amountB = BigInt("1000000000000000000"),
      owner = "0xb0b12b5ded3a6ae85f234bb60726f00b1ecd2ec1",
      feeAmount = BigInt("1000000000000000000"),
      dualAuthAddress = "0xa876ab5c19ff8c8065b1114cf7dfe9d09686afbf",
      allOrNone = false,
      validSince = 1542012728,
      wallet = "0xb5eb3fd0a6011cb1a47ddbde913744d544597d3e",
      walletSplitPercentage = 20,
      tokenReceipt = "0xb0b12b5ded3a6ae85f234bb60726f00b1ecd2ec1",
      feeToken = "0x3b243b0e87228aa330a56e0af3f2733f9c780b44",
      sig = "0x00411ba54418d6f77e03c47461fdca3ec6cc5bbb65edc13d824f43015e2913b1fb84495b33d1071577795e3514ddd5e298abb1c2f1e6562c0c05587edd1063b26eb36e",
      dualAuthSig = "0x00411bbe5a02bef3248f86f78c7577fc6b5afb87d3d5e87b57a1e4e582c6ef347bc9ac1c50f9b76d83c4a19f1ead95a982d1ff914d5eaab67554d2df795e7ece86ca07",
      tokenSpendableS = 2,
      tokenSpendableFee = 3
    )
    val order2 = raworder2.copy(hash = raworder2.cryptoHash)

    val ring = Ring(
      orders = Seq(order1, order2),
      miner = "0xcd48d3d60bd4f0c2c5d9188f6e4f4d5d6f0b0d39",
      feeReceipt = "0xfff0e52e473c384a57e32b9394fba174b4849756",
      transactionOrigin = "0x77ddd79b1c8345809b5b7f25cd0058d211471eb0",
      sig = "0x00411c6de0e2b3f39640d1d9d334c37d99d1caaaaacdb1f3748bef9abcf6a3f60186b26392784ed8bb7041e6ca0eb0faefdc99cd00925033c05d2223758ae82c1b0395",
      ringOrderIndex = Seq(Seq(0, 1))
    )

    order1.hash should be("0x93b65781da000e15a72736bde8e7288956a68b89868a400f72f17d6aca99924d")
    order2.hash should be("0x71ddf04504887e77e7fc02e23c671305cac94926cb230285d32b5bd5aae78850")

    val serializeRes = generator.serialize(ring)
    info("generated ring data: " + serializeRes)
    serializeRes should be(originInput)

    val deserializeRes = deserializer.deserialize(originInput)
    deserializeRes.orders.size should be(2)
    compare(deserializeRes.orders.head, order1) should be(true)
    compare(deserializeRes.orders.last, order2) should be(true)
  }

  private def compare(src: Order, dst: Order): Boolean = {
    (src.owner eqCaseInsensitive dst.owner) &&
      (src.tokenS eqCaseInsensitive dst.tokenS) &&
      (src.tokenB eqCaseInsensitive dst.tokenB) &&
      (src.amountS == dst.amountS) &&
      (src.amountB == dst.amountB) &&
      (src.feeAmount == dst.feeAmount) &&
      (src.feeToken eqCaseInsensitive dst.feeToken) &&
      (src.dualAuthAddress eqCaseInsensitive dst.dualAuthAddress) &&
      (src.allOrNone == dst.allOrNone) &&
      (src.validSince == dst.validSince) &&
      (src.validUntil == dst.validUntil) &&
      (src.wallet eqCaseInsensitive dst.wallet) &&
      (src.walletSplitPercentage == dst.walletSplitPercentage) &&
      (src.tokenReceipt eqCaseInsensitive dst.tokenReceipt) &&
      (src.sig == dst.sig) &&
      (src.dualAuthSig == dst.dualAuthSig)
  }
}
