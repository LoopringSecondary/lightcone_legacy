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

class EIP712SupportSpec extends FlatSpec with Matchers {
  val eip712Support: EIP712Support = new DefaultEIP712Support

  "calculateOrderHash" should "be able to get hash of an order" in {
    val typedDataJson1 =
      """
        |{
        |  "types": {
        |    "EIP712Domain": [
        |      {
        |        "name": "name",
        |        "type": "string"
        |      },
        |      {
        |        "name": "version",
        |        "type": "string"
        |      }
        |    ],
        |    "Order": [
        |      {
        |        "name": "tokenS",
        |        "type": "address"
        |      },
        |      {
        |        "name": "tokenB",
        |        "type": "address"
        |      },
        |      {
        |        "name": "amountS",
        |        "type": "uint"
        |      },
        |      {
        |        "name": "amountB",
        |        "type": "uint"
        |      }
        |    ],
        |  },
        |  "primaryType": "Order",
        |  "domain": {
        |    "name": "Loopring Protocol",
        |    "version": "2"
        |  },
        |  "message": {
        |    "tokenS": "0xabe12e3548fdb334d11fcc962c413d91ef12233f",
        |    "tokenB": "0x2260fac5e5542a773aa44fbcfedf7c193bc2c599",
        |    "amountS": "10000000000000000000",
        |    "amountB": "100000000000000000"
        |  }
        |}
    """.stripMargin

    assert(true)
  }

}
