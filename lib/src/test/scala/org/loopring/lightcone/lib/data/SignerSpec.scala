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

class SignerSpec extends FlatSpec with Matchers {

  // curl http://127.0.0.1:8545/ -X POST --data '{"jsonrpc":"2.0","method":"eth_sign","params":["0x2c99c120bfafc5c748139f2202430afda9d92fcd", "a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"],"id":1}'

  "ethereumAlgorithmTest1" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z ethereumAlgorithm'")

    val publicKey = "0x2c99c120bfafc5c748139f2202430afda9d92fcd"
    val privateKey = "0xd7d51bdb8b4072b92d5401eae5e76c327d6a7ab013a637579dc4803b19209ea3"
    val hash = "0xa1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"
    val encode = "0x00411b4be4e9cd8f233e6a95816f540918ccf02462f9ada2c91e5f51fd260b02984a89741864d4fa5a6607e37104f9c11f5ab38fe94ac5bfbc4424c24ee9b9d50e0c35"

    val signer = new Signer(privateKey)
    signer.address should be(publicKey)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_ETHEREUM, hash)
    sig should be(encode)
  }

  "eipAlgorithmTest1" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z eipAlgorithmTest1'")

    val publicKey = "0x5b88d580cef81e8c7a30b34f5ea7c79c301fe215"
    val privateKey = "0x0e42f327ee3cfa7ccfc084a0bb68d05eb627610303012a67afbf1ecd9b0d32fa"
    val hash = "0xa1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1"
    val encode = "0x01411b1baa95b372065d60efbb768c95e8c80625802e67c98059624ab3c2debdd4e0e04a5747a36d1482881575e1b0e45e103c53081c4f8b86b8c9bdfd1abf9352aa7d"

    val signer = new Signer(privateKey)
    signer.address should be(publicKey)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_EIP712, hash)
    sig should be(encode)
  }

  "ethereumAlgorithmTest2" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z ethereumAlgorithmTest2'")

    val publicKey = "0x06c2422e8ebdf785139a29fe7d12f52c8c578a25"
    val privateKey = "0xa55be424bded6dfc2fc4bf8184cee9dc8239c226c8312af9584073c9ba975a6e"
    val hash = "0x87af0e69eadbad669423455af52cfad68ab75ec9e86288cf31bac18e9b881d7a"
    val encode = "0x00411b6423d0a6b318d9b7d04345ccd2334603bb0b18439c1afe5b107868c233e1b868725646bf542f1047d355dbaa5ca29d4dc2a5ec02858bb35d4a43c604383b48fc"

    val signer = new Signer(privateKey)
    signer.address should be(publicKey)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_ETHEREUM, hash)
    sig should be(encode)
  }

  "eipAlgorithmTest2" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z eipAlgorithmTest2'")

    val publicKey = "0x1b978a1d302335a6f2ebe4b8823b5e17c3c84135"
    val privateKey = "0x5b791c6c9f4b7aa95ccb58f0f939397d1dcd047a5c0231e77ca353ebfea306f3"
    val hash = "0xa5040e8f5ea24f4b6c053caaa19c44608e5c33e5f71ad6ee48f97241d597ed3e"
    val encode = "0x01411c65ff9de1f8ffc99a9810933775e096f6b2bf37f9d1a0c8d06193575683080fec0356a04e4ba54b60346da4de52722bea63f3415a15b98c3b35d86e2c0d4c7d0b"

    val signer = new Signer(privateKey)
    signer.address should be(publicKey)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_EIP712, hash)
    sig should be(encode)
  }

  "ethereumAlgorithmTest3" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z ethereumAlgorithmTest3'")

    val publicKey = "0x1c7e4dc380e5f3b4f833f73d6ba13f2d9524f7ee"
    val privateKey = "0x49428e80ed1483475190b9a85b60c7108f830485bb9deaaa346bd0c3fbdbbe4a"
    val hash = "0x74fb9d6967d6911e2edbf02567630c4dd2fb6207df3f4099d808bd4a1b0a6796"
    val encode = "0x00411b743cef12886038db7dfc1918f7a6e6f12b675d0d7dd023be99adfb0f41a0c17c515a4f7f07fbe1fcee807dd6cf1abaaf549bb0ec654cdc74937cd129852b6069"

    val signer = new Signer(privateKey)
    signer.address should be(publicKey)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_ETHEREUM, hash)
    sig should be(encode)
  }

  "eipAlgorithmTest3" should "be able to verify signed data" in {
    info("sbt lib/'testOnly *SignerSpec -- -z eipAlgorithmTest3'")

    val hash = "0x6081726af4c7fd1795465d8f1170667b2d6c411e8bced8cb0609a5f4edee0ec4"
    val originSig = "0x01411c4d663312f6e3308c84c94cd5d2ecc18eb871355aa72ac331b92d79dab64e07cd0441d614569bf4f50f5c2145d7f3c90d56818cc337b5e221df00492d389482bd"
    val owner = "0x0031cc4352f7d6074e4b5c9c9e0b9645f91780e4"
    val privateKey = "4784bfc63cdf0daa49d7a5572542acc5a9423f9f38f2393120aefe388c62cf77"

    val signer = new Signer(privateKey)
    signer.address should be(owner)

    val sig = signer.signHash(SignAlgorithm.ALGORITHM_EIP712, hash)
    sig should be(originSig)
  }
}
