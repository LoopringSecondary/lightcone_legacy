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

class EIP712SupportImplSpec extends FlatSpec with Matchers {
  val eip712Support: EIP712Support = new DefaultEIP712Support

  "jsonToTypedData" should "be able to convert a valid json to EIP712TypedData object" in {
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

    val res = eip712Support.jsonToTypedData(typedDataJson1)
    assert(res.isInstanceOf[EIP712TypedData], "json to typed data failed.")
  }

  /**
    * all numbers must be represented as a hex string.
    */
  "getEIP712Message" should "be able to calculate eip712 hash for an EIP712TypedData object" in {
    val hashExpected1 =
      "0xab7eee69af0a7eb403b6f7e5ec5a27634df92b0319692f90bbbc0d3e07e23a9c"
    val orderTypedDataJson1 =
      """
      {
        "types": {
          "EIP712Domain": [
            {
              "name": "name",
              "type": "string"
            },
            {
              "name": "version",
              "type": "string"
            }
          ],
          "Order": [
            {
              "name": "amountS",
              "type": "uint"
            },
            {
              "name": "amountB",
              "type": "uint"
            },
            {
              "name": "feeAmount",
              "type": "uint"
            },
            {
              "name": "validSince",
              "type": "uint"
            },
            {
              "name": "validUntil",
              "type": "uint"
            },
            {
              "name": "owner",
              "type": "address"
            },
            {
              "name": "tokenS",
              "type": "address"
            },
            {
              "name": "tokenB",
              "type": "address"
            },
            {
              "name": "dualAuthAddr",
              "type": "address"
            },
            {
              "name": "broker",
              "type": "address"
            },
            {
              "name": "orderInterceptor",
              "type": "address"
            },
            {
              "name": "wallet",
              "type": "address"
            },
            {
              "name": "tokenRecipient",
              "type": "address"
            },
            {
              "name": "feeToken",
              "type": "address"
            },
            {
              "name": "walletSplitPercentage",
              "type": "uint16"
            },
            {
              "name": "tokenSFeePercentage",
              "type": "uint16"
            },
            {
              "name": "tokenBFeePercentage",
              "type": "uint16"
            },
            {
              "name": "allOrNone",
              "type": "bool"
            },
            {
              "name": "tokenTypeS",
              "type": "uint8"
            },
            {
              "name": "tokenTypeB",
              "type": "uint8"
            },
            {
              "name": "tokenTypeFee",
              "type": "uint8"
            },
            {
              "name": "trancheS",
              "type": "bytes32"
            },
            {
              "name": "trancheB",
              "type": "bytes32"
            },
            {
              "name": "transferDataS",
              "type": "bytes"
            }
          ]
        },
        "primaryType": "Order",
        "domain": {
          "name": "Loopring Protocol",
          "version": "2"
        },
        "message": {
          "amountS": "de0b6b3a7640000",
          "amountB": "3635c9adc5dea00000",
          "feeAmount": "de0b6b3a7640000",
          "validSince": "5c6e4ce1",
          "validUntil": "0",
          "owner": "0xFDa769A839DA57D88320E683cD20075f8f525a57",
          "tokenS": "0xDeedA202202793FdbA4a8847C366888BD61C62CE",
          "tokenB": "0xB7735664247664C1666536728896C5A723A649d7",
          "dualAuthAddr": "0x66D3444ad66fc32abCEC9B38A4181066b1146CCA",
          "broker": "",
          "orderInterceptor": "",
          "wallet": "0xb00e7AEBE72AA3D61aFE6c0AC19619FE39c5D326",
          "tokenRecipient": "0xFDa769A839DA57D88320E683cD20075f8f525a57",
          "feeToken": "0xB7735664247664C1666536728896C5A723A649d7",
          "walletSplitPercentage": "0xa",
          "tokenSFeePercentage": 0,
          "tokenBFeePercentage": 0,
          "allOrNone": false,
          "tokenTypeS": 0,
          "tokenTypeB": 0,
          "tokenTypeFee": 0,
          "trancheS": "0x0000000000000000000000000000000000000000000000000000000000000000",
          "trancheB": "0x0000000000000000000000000000000000000000000000000000000000000000",
          "transferDataS": "0x"
        }
      }
      """.stripMargin

    val res = eip712Support.jsonToTypedData(orderTypedDataJson1)
    eip712Support.getEIP712Message(res) should be(hashExpected1)
  }

}
