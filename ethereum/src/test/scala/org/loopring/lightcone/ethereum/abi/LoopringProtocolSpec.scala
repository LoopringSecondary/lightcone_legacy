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

class LoopringProtocolSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val loopringProtocolAbi = LoopringProtocolAbi()

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "decodeInvalidRingEvent" should "decode data to InvalidRingEvent result " in {
    val data = "0xea7d4e25f31194081fa0749437799e324b762e131575e7d3f9043b079503f77d"
    val topics = Seq(
      "0x977ca9d66ddf6e18cac50dbf89ee6dcce72d4635c60a13314945868798f73cdb"
    )
    val resultOpt = loopringProtocolAbi.unpackEvent(data, topics.toArray)
    resultOpt match {
      case None ⇒
      case Some(res: InvalidRingEvent.Result) ⇒
        res._ringHash should be("0xea7d4e25f31194081fa0749437799e324b762e131575e7d3f9043b079503f77d")
    }
  }

  "decodeSubmitRingsFunction" should "decode input to submitRings function Params" in {
    val input = "0xcbaef59a0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000041f00000002000100040008000d00120000002b00300035003a0042004a00000001004b00000000005000000055006e0000000000870000000000000000000a0000000000000000000000000000008f003500300094009c00a40002000300a5000000000050000000aa00c3000000000087000000000000000000140000000000000000000000000200010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000091e658c123e23f509201a99f30574f65487246393f5e88f29d0c0da7f984d9b932b3e7f2e1f376bc000000000000000000000000000000000000000000000000000000000000004300411b5c0979144307872b3493d10f7150682e6409fbc11cd2a7bd77a90df3779cf375342aadbeae617443dd079f94762a172cc2355731e0557d5581af173e4ae016a40007d24603d5fb6cdff728a7be7a04a26b7fcc20d91927ac2b906bf84aba7d0d69faee3dc6aa6249d80e912fdda101d71285aa5c11575dea2738c1d8ed0000000000000000000000000000000000000000000000056bc75e2d631000000000000000000000000000000000000000000000000000008ac7230489e80000639c0907fcef8213fc35118789b2ceb6fa7c651deb37ad078a4e7653afa6f2d23dd56ebc1877d0dedbfcc37c000000000000000000000000000000000000000000000000000000000000004300411c212b88c0d767a6be39a4bda7320078f9e3eaa620f8d37b29904dbd3e171f46213a5442c50fa07a954ee5f2daf54678e337514c3ee8376184c86f81b2b774320a00000000000000000000000000000000000000000000000000000000000000004300411c109239d87ce784dd43144928a492f546f6c379efa07fe11f9865f1332d9b497f03e6f5693a5dd2e15c9e00c3e05461122d7677d595981a7d84713d4bda3fb19d000000000000000000000000000000000000000000000000000de0b6b3a7640000cc1cf2a03c023e12426b0047c3d07e30f4e1d1030000000000000000000000000000000000000000000000004563918244f4000000000000000000000000000000000000000000000000000270801d946c940000639c090c98b792b590f3719d52eb1596e685a1b2dd0a71b2000000000000000000000000000000000000000000000000000000000000004301411b8b4bb043eaca2adb7130105c6ba18c5ceb4466f55cfc597ca27dc830551434804d85ddfaa43f58d3aa586b3d842a908d15f7b60eeadda1f7c051a122bb61b1bd00000000000000000000000000000000000000000000000000000000000000004300411b7cc7ba836c29d27e730dfc46438f62b8abba86f4b2ed7604e3d2b5f9bff7a81967b5ec21aa52d44e03ba3c2b331bf6bc779faf1b5f6c3ae6bb37a0cbbfffd8650000"
    val paramsOpt = loopringProtocolAbi.unpackFunctionInput(input)

    paramsOpt match {
      case None ⇒
      case Some(param: SubmitRingsFunction.Params) ⇒
        Numeric.toHexString(param.data) should be("0x00000002000100040008000d00120000002b00300035003a0042004a00000001004b00000000005000000055006e0000000000870000000000000000000a0000000000000000000000000000008f003500300094009c00a40002000300a5000000000050000000aa00c3000000000087000000000000000000140000000000000000000000000200010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000091e658c123e23f509201a99f30574f65487246393f5e88f29d0c0da7f984d9b932b3e7f2e1f376bc000000000000000000000000000000000000000000000000000000000000004300411b5c0979144307872b3493d10f7150682e6409fbc11cd2a7bd77a90df3779cf375342aadbeae617443dd079f94762a172cc2355731e0557d5581af173e4ae016a40007d24603d5fb6cdff728a7be7a04a26b7fcc20d91927ac2b906bf84aba7d0d69faee3dc6aa6249d80e912fdda101d71285aa5c11575dea2738c1d8ed0000000000000000000000000000000000000000000000056bc75e2d631000000000000000000000000000000000000000000000000000008ac7230489e80000639c0907fcef8213fc35118789b2ceb6fa7c651deb37ad078a4e7653afa6f2d23dd56ebc1877d0dedbfcc37c000000000000000000000000000000000000000000000000000000000000004300411c212b88c0d767a6be39a4bda7320078f9e3eaa620f8d37b29904dbd3e171f46213a5442c50fa07a954ee5f2daf54678e337514c3ee8376184c86f81b2b774320a00000000000000000000000000000000000000000000000000000000000000004300411c109239d87ce784dd43144928a492f546f6c379efa07fe11f9865f1332d9b497f03e6f5693a5dd2e15c9e00c3e05461122d7677d595981a7d84713d4bda3fb19d000000000000000000000000000000000000000000000000000de0b6b3a7640000cc1cf2a03c023e12426b0047c3d07e30f4e1d1030000000000000000000000000000000000000000000000004563918244f4000000000000000000000000000000000000000000000000000270801d946c940000639c090c98b792b590f3719d52eb1596e685a1b2dd0a71b2000000000000000000000000000000000000000000000000000000000000004301411b8b4bb043eaca2adb7130105c6ba18c5ceb4466f55cfc597ca27dc830551434804d85ddfaa43f58d3aa586b3d842a908d15f7b60eeadda1f7c051a122bb61b1bd00000000000000000000000000000000000000000000000000000000000000004300411b7cc7ba836c29d27e730dfc46438f62b8abba86f4b2ed7604e3d2b5f9bff7a81967b5ec21aa52d44e03ba3c2b331bf6bc779faf1b5f6c3ae6bb37a0cbbfffd86500")
    }
  }

}
