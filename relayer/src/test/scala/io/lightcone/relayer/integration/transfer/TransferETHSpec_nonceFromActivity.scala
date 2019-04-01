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
import io.lightcone.lib.Address
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.lib.NumericConversion._
import scala.concurrent.Await

class TransferETHSpec_nonceFromActivity
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with AccountHelper
    with ActivityHelper
    with Matchers {

  feature(
    "transfer out some ETH to verify account's nonce => from continuous activities max nonce + 1"
  ) {
    scenario("transfer ETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = getUniqueAccount()

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
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            902,
            "0x2c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            2,
            "17".zeros(18),
            "23".zeros(18)
          )
        )
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            903,
            "0x3c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            3,
            "16".zeros(18),
            "24".zeros(18)
          )
        )
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            904,
            "0x4c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            4,
            "15".zeros(18),
            "25".zeros(18)
          )
        )
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            905,
            "0x5c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            5,
            "14".zeros(18),
            "26".zeros(18)
          )
        )
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            906,
            "0x6c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            6,
            "13".zeros(18),
            "27".zeros(18)
          )
        )
        .++(
          ethTransferConfirmedActivities(
            account.getAddress,
            to.getAddress,
            907,
            "0x7c6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
            "1".zeros(18),
            7,
            "12".zeros(18),
            "28".zeros(18)
          )
        )
        .foreach(eventDispatcher.dispatch)
      Thread.sleep(2000)

      Then("verify activities and balances")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 8 && res.activities
              .forall(a => a.txStatus == TxStatus.TX_STATUS_SUCCESS)
          })
        )
      GetActivities
        .Req(to.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            res.activities.length == 8 && res.activities
              .forall(a => a.txStatus == TxStatus.TX_STATUS_SUCCESS)
          })
        )

      val amount = "8".zeros(18) // totally 8 eth transfer
      val ethBalance = fromInitBalanceRes.getAccountBalance.tokenBalanceMap(
        Address.ZERO.toString
      )
      val ethExpect = ethBalance.copy(
        balance = toBigInt(ethBalance.balance) - amount,
        availableBalance = toBigInt(ethBalance.availableBalance) - amount
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

      val ethBalance2 = toInitBalanceRes.getAccountBalance.tokenBalanceMap(
        Address.ZERO.toString
      )
      val ethExpect2 = ethBalance2.copy(
        balance = toBigInt(ethBalance2.balance) + amount,
        availableBalance = toBigInt(ethBalance2.availableBalance) + amount
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

      val res = Await.result(
        (entryPointActor ? GetAccountNonce.Req(account.getAddress))
          .mapTo[GetAccountNonce.Res],
        timeout.duration
      )
      res.nonce should be(8)
    }
  }
}
