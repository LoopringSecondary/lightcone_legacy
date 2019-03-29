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

import io.lightcone.relayer._
import io.lightcone.relayer.data.{GetAccount, GetActivities}
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._
import io.lightcone.ethereum.TxStatus
import io.lightcone.lib.Address
import io.lightcone.relayer.actors.ActivityActor
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration.helper.{AccountHelper, ActivityHelper}
import io.lightcone.lib.NumericConversion._

class TransferETHSpec_success
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature("transfer some ETH to verify activities and balances") {
    scenario("transfer ETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = getUniqueAccount()
      val blockNumber = 987L
      val nonce = 11L

      Given("initialize eth balance")
      mockAccountWithFixedBalance(account.getAddress, dynamicMarketPair)
      mockAccountWithFixedBalance(to.getAddress, dynamicMarketPair)

      Then("check initialize balance")
      val getFromAddressBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      val getToAddressBalanceReq = GetAccount.Req(
        to.getAddress,
        allTokens = true
      )
      val fromInitBalanceRes =
        getFromAddressBalanceReq.expectUntil(
          initializeMatcher(dynamicMarketPair)
        )
      val toInitBalanceRes = getToAddressBalanceReq.expectUntil(
        initializeMatcher(dynamicMarketPair)
      )

      When("send some transfer events")
      ethTransferPendingActivities(
        account.getAddress,
        to.getAddress,
        blockNumber,
        txHash,
        "10".zeros(18),
        nonce
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(1000)

      Then("the each account should query one pending activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_PENDING
          })
        )
      GetActivities
        .Req(to.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_PENDING
          })
        )

      When("activities confirmed")
      val blockEvent =
        blockConfirmedEvent(account.getAddress, blockNumber, txHash, nonce)
      ActivityActor.broadcast(blockEvent)
      Thread.sleep(2000)

      val transferAmount = "10".zeros(18)
      ethTransferConfirmedActivities(
        account.getAddress,
        to.getAddress,
        blockNumber,
        txHash,
        transferAmount,
        nonce,
        "10".zeros(18),
        "30".zeros(18)
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(1000)

      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )
      GetActivities
        .Req(to.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )

      val ethBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        Address.ZERO.toString
      )
      val ethExpect = ethBalance.copy(
        balance = toBigInt(ethBalance.balance) - transferAmount,
        availableBalance = toBigInt(ethBalance.availableBalance) - transferAmount
      )
      getFromAddressBalanceReq.expectUntil(
        balanceMatcher(
          ethExpect,
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            WETH_TOKEN.address
          ),
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            LRC_TOKEN.address
          ),
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.baseToken
          ),
          fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.quoteToken
          )
        )
      )
      val ethBalance2 = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        Address.ZERO.toString
      )
      val ethExpect2 = ethBalance2.copy(
        balance = toBigInt(ethBalance2.balance) + transferAmount,
        availableBalance = toBigInt(ethBalance2.availableBalance) + transferAmount
      )
      getToAddressBalanceReq.expectUntil(
        balanceMatcher(
          ethExpect2,
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            WETH_TOKEN.address
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            LRC_TOKEN.address
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.baseToken
          ),
          toInitBalanceRes.getAccountBalance.tokenBalanceMap(
            dynamicMarketPair.quoteToken
          )
        )
      )
    }
  }
}
