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

package io.lightcone.relayer.integration

import io.lightcone.ethereum.TxStatus
import io.lightcone.relayer._
import io.lightcone.relayer.actors.ActivityActor
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.helper._
import org.scalatest._
import akka.pattern._
import scala.concurrent.Await

class TransferETHSpec_nonceFromChain
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("transfer out some ERC20 token to verify activities => get a larger nonce on chain") {
    scenario("transfer ETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = getUniqueAccount()

      Given("initialize eth balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)
      mockAccountWithFixedBalance(to.getAddress, dynamicMarketPair)

      Given("initialize nonce on chain with 10")
      addGetNonceExpects({
        case req =>
          GetNonce.Res(result = "a")
      })

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      val getToAddressBalanceReq = GetAccount.Req(
        to.getAddress,
        allTokens = true
      )
      getFromAddressBalanceReq.expectUntil(initializeCheck(dynamicMarketPair))
      getToAddressBalanceReq.expectUntil(
        initializeCheck(dynamicMarketPair)
      )

      When("activities confirmed")
      val blockEvent =
        blockConfirmedEvent(account.getAddress, 0L, txHash, 100)
      ActivityActor.broadcast(blockEvent)
      Thread.sleep(2000)

      ethTransferConfirmedActivities(
        account.getAddress,
        to.getAddress,
        900,
        "0x0c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
        "1".zeros(18),
        0,
        "19".zeros(18),
        "21".zeros(18)
      ).++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            901,
            "0x1c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            1,
            "18".zeros(18),
            "22".zeros(18)
          )
        )
        .foreach(eventDispatcher.dispatch)
      Thread.sleep(2000)

      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 2 && !res.activities
              .exists(_.txStatus != TxStatus.TX_STATUS_SUCCESS)
          })
        )
      GetActivities
        .Req(to.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 2 && !res.activities
              .exists(_.txStatus != TxStatus.TX_STATUS_SUCCESS)
          })
        )

      val res = Await.result(
        (entryPointActor ? GetAccountNonce.Req(account.getAddress))
          .mapTo[GetAccountNonce.Res],
        timeout.duration
      )
      res.nonce should be(10)
    }
  }
}
