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

package org.loopring.lightcone.actors.entrypoint

import org.loopring.lightcone.actors.ethereum.{
  ringSubmitterAbi,
  EthereumAccessActor
}
import org.loopring.lightcone.actors.support.{CommonSpec, EthereumSupport}
import org.loopring.lightcone.ethereum.abi.SubmitRingsFunction
import org.loopring.lightcone.ethereum.ethereum.getSignedTxData
import org.loopring.lightcone.ethereum.data.{Address, Transaction}
import org.loopring.lightcone.proto.EthGetBalance
import org.scalatest.WordSpec
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import akka.pattern._

import scala.concurrent.Await

class SendTransaction
    extends CommonSpec("""akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "ethereum_access",
                         | "ethereum_query",
                         | "ethereum_client_monitor",
                         | "ring_settlement"]""".stripMargin)
    with EthereumSupport {
  "send an orderbook request" must {
    "receive a response without value" in {

      val ethereumAccessorActor = actors.get(EthereumAccessActor.name)
      val f = (ethereumAccessorActor ? EthGetBalance.Req(
        address = Address("0xe5fd5be7c9a50358302de473db7818c7a91d1ec0").toString,
        tag = "latest"
      ))

      val r = Await.result(f, timeout.duration)
      println(r)

      implicit val credentials: Credentials =
        Credentials.create(
          "0x4e37ce13f9370ea0f86da42ffb24ef0f177ba7a1d777a78d050320e425a591df"
        )

      val tx = Transaction(
        inputData = "",
        nonce = 0,
        gasLimit = BigInt("210000"),
        gasPrice = BigInt("200000"),
        to = "0xe5fd5be7c9a50358302de473db7818c7a91d1ec0",
        value = BigInt("1000000000000000000")
      )
      val rawTx = getSignedTxData(tx)

      println(s"${rawTx}")
    }
  }
}
