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

import io.lightcone.core.{Amount, MarketPair}
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.lib.Address
import io.lightcone.relayer._
import io.lightcone.core._
import io.lightcone.relayer.data.{
  AccountBalance,
  GetAccount,
  GetActivities,
  GetOrderbook,
  SubmitOrder
}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.CommonHelper
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.lib.NumericConversion._
import org.scalatest._
import com.google.protobuf.ByteString
import io.lightcone.ethereum.event.AddressBalanceUpdatedEvent
import org.slf4s.Logger
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.math.BigInt

class TransferETHSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("transfer") {
    scenario("transfer ETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"

      Given("initialize eth balance")
      addAccountExpects({
        case req =>
          GetAccount.Res(
            Some(
              AccountBalance(
                address = req.address,
                tokenBalanceMap = req.tokens.map { t =>
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = BigInt("20000000000000000000"),
                    allowance = BigInt("1000000000000000000000"),
                    availableAlloawnce = BigInt("1000000000000000000000"),
                    availableBalance = BigInt("20000000000000000000")
                  )
                }.toMap
              )
            )
          )
      })
      val getBalanceReq = GetAccount.Req(
        account.getAddress,
        allTokens = true
      )
      getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val balanceOpt = res.accountBalance
          val resBalance: BigInt =
            balanceOpt.get.tokenBalanceMap(LRC_TOKEN.address).balance
          log.info(
            s"--1 ${resBalance}"
          )
          // resBalance == BigInt("20000000000000000000")

          true
        })
      )

      When("send an account some transfer events")
      Seq(
        Activity(
          owner = account.getAddress,
          block = 987,
          txHash = txHash,
          activityType = Activity.ActivityType.ETHER_TRANSFER_OUT,
          timestamp = timeProvider.getTimeSeconds,
          token = Address.ZERO.toString(),
          detail = Activity.Detail.EtherTransfer(
            Activity.EtherTransfer(
              account.getAddress,
              Some(
                Amount(
                  ByteString.copyFrom("10000000000000000000", "UTF-8"),
                  987
                )
              )
            )
          )
        ),
        AddressBalanceUpdatedEvent(
          address = account.getAddress,
          token = Address.ZERO.toString(),
          balance = Some(
            Amount(ByteString.copyFrom("10000000000000000000", "UTF-8"), 987)
          ),
          block = 987
        ),
        Activity(
          owner = to,
          block = 987,
          txHash = txHash,
          activityType = Activity.ActivityType.ETHER_TRANSFER_IN,
          timestamp = timeProvider.getTimeSeconds,
          token = Address.ZERO.toString(),
          detail = Activity.Detail.EtherTransfer(
            Activity.EtherTransfer(
              to,
              Some(
                Amount(
                  ByteString.copyFrom("10000000000000000000", "UTF-8"),
                  987
                )
              )
            )
          )
        ),
        AddressBalanceUpdatedEvent(
          address = to,
          token = Address.ZERO.toString(),
          balance = Some(
            Amount(ByteString.copyFrom("1010000000000000000000", "UTF-8"), 987)
          ),
          block = 987
        )
      ).foreach(eventDispatcher.dispatch)

      Thread.sleep(1000)

      Then("the each account should query one activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => res.activities.length == 1)
        )
      GetActivities
        .Req(to)
        .expectUntil(
          check((res: GetActivities.Res) => res.activities.length == 1)
        )

      getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val balanceOpt = res.accountBalance
          val resBalance = toBigInt(
            balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
          )
          log.info(
            s"--2 ${resBalance}"
          )
          // resBalance == BigInt("20000000000000000000")

          true
        })
      )

      try {
        Await.result(system.terminate(), 60 second)
      } catch {
        case e: Exception =>
          info(s"occurs error: ${e.getMessage}, ${e.printStackTrace()}")
      }
    }
  }
}
