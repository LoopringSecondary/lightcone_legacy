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
import io.lightcone.relayer.data.{GetAccount, GetActivities}
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.helper._
import org.scalatest._

class ApproveNotLoopringSpec_notAffectAllowance
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with ValidateHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("not Loopring protocol approved will not affect Loopring allowance") {
    scenario("approve test") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val blockNumber = 987L
      val nonce = 11L

      Given("initialize balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      getFromAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )

      When("not Loopring approved activities confirmed")
      notLoopringApproveConfirmedActivities(
        account.getAddress,
        blockNumber,
        txHash,
        dynamicMarketPair.baseToken,
        "2000".zeros(18),
        nonce
      ).foreach(eventDispatcher.dispatch)

      Then("verify confirmed activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )

      Then("verify balance and allowance not affected")
      getFromAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )
    }
  }
}
