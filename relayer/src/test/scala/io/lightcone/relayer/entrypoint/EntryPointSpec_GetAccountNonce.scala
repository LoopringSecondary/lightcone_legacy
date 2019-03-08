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

package io.lightcone.relayer.entrypoint

import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent._

class EntryPointSpec_GetAccountNonce
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  val account = getUniqueAccountWithoutEth
  override def beforeAll(): Unit = {
    Await.result(
      transferEth(account.getAddress, "10")(accounts(0)),
      timeout.duration
    )
    super.beforeAll()
  }

  "the nonce after send an ethereum transaction" must {
    "be changed" in {
      val nonce1 = Await.result(
        singleRequest(
          GetAccountNonce.Req(account.getAddress),
          "get_account_nonce"
        ).mapTo[GetAccountNonce.Res],
        timeout.duration
      )
      info(s"nonce of account1 is ${nonce1.nonce}")

      Await.result(
        transferEth(accounts(0).getAddress, "3")(account),
        timeout.duration
      )
      val nonce2 = Await.result(
        singleRequest(
          GetAccountNonce.Req(account.getAddress),
          "get_account_nonce"
        ).mapTo[GetAccountNonce.Res],
        timeout.duration
      )
      info(s"nonce of account1 is ${nonce2.nonce}")

      nonce2.nonce should be(nonce1.nonce + 1)
    }
  }

}
