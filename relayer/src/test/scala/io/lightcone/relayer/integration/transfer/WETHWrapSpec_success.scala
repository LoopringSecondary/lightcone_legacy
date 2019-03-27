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
import io.lightcone.relayer.data.{GetAccount, GetActivities}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.helper._
import org.scalatest._

class WETHWrapSpec_success
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("WETH wrap success") {
    scenario("wrap WETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val blockNumber = 987L
      val nonce = 11L

      Given("initialize eth balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      getFromAddressBalanceReq.expectUntil(initializeCheck(dynamicMarketPair))

      When("send some convert events")
      wrapWethPendingActivities(
        account.getAddress,
        blockNumber,
        txHash,
        "10".zeros(18),
        nonce
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(1000)

      Then("the account should query 2 pending activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 2 && !res.activities
              .exists(a => a.txStatus != TxStatus.TX_STATUS_PENDING)
          })
        )

      When("activities confirmed")
      val blockEvent =
        blockConfirmedEvent(account.getAddress, blockNumber, txHash, nonce)
      ActivityActor.broadcast(blockEvent)
      Thread.sleep(2000)

      wethWrapConfirmedActivities(
        account.getAddress,
        blockNumber,
        txHash,
        "10".zeros(18),
        nonce,
        "10".zeros(18),
        "40".zeros(18)
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(1000)

      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 2 && !res.activities
              .exists(a => a.txStatus != TxStatus.TX_STATUS_SUCCESS)
          })
        )

      getFromAddressBalanceReq.expectUntil(
        balanceCheck(
          dynamicMarketPair,
          Seq("10", "10", "50", "50", "60", "60", "400", "400")
        ) and
          wethBalanceCheck(
            "40",
            "40"
          )
      )
    }
  }
}
