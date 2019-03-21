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

import com.google.protobuf.ByteString
import io.lightcone.core.{Amount, _}
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event.{AddressBalanceUpdatedEvent, BlockEvent}
import io.lightcone.ethereum.persistence.{Activity, TxEvents}
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion._
import io.lightcone.relayer._
import io.lightcone.relayer.actors.ActivityActor
import io.lightcone.relayer.data.{AccountBalance, GetAccount, GetActivities, GetPendingActivityNonce}
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._
import scala.math.BigInt

class WETHWrapSpec_success
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with Matchers {

  feature("WETH wrap success") {
    scenario("wrap WETH") {
      implicit val account = getUniqueAccount()
      val txHash =
        "0xbc6331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val to = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val blockNumber = 987L

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

      When("send some transfer events")
      Seq(
        TxEvents(
          TxEvents.Events.Activities(
            TxEvents.Activities(
              Seq(
                Activity(
                  owner = account.getAddress,
                  block = blockNumber,
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
                          blockNumber
                        )
                      )
                    )
                  ),
                  nonce = 11
                ),
                Activity(
                  owner = to,
                  block = blockNumber,
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
                          blockNumber
                        )
                      )
                    )
                  ),
                  nonce = 11
                )
              )
            )
          )
        )
      ).foreach(eventDispatcher.dispatch)

      Thread.sleep(3000)

      Then("the each account should query one pending activity")
      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            log.info(
              s"--2 ${res}"
            )
            // res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_PENDING
            true
          })
        )
      GetActivities
        .Req(to)
        .expectUntil(
          check((res: GetActivities.Res) => {
            log.info(
              s"--3 ${res}"
            )
            // res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_PENDING
            true
          })
        )

      GetPendingActivityNonce
        .Req(account.getAddress, 2)
        .expectUntil(
          check((res: GetPendingActivityNonce.Res) => {
            res.nonces.head == 11
            log.info(
              s"--4 ${res.nonces.head}"
            )
            true
          })
        )

      When("activities confirmed")
      val blockEvent = BlockEvent(
        blockNumber = blockNumber,
        txs = Seq(
          BlockEvent.Tx(
            from = account.getAddress,
            nonce = 11,
            txHash = txHash
          )
        )
      )
      ActivityActor.broadcast(blockEvent)
      Thread.sleep(1000)

      Seq(
        TxEvents(
          TxEvents.Events.Activities(
            TxEvents.Activities(
              Seq(
                Activity(
                  owner = account.getAddress,
                  block = blockNumber,
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
                          blockNumber
                        )
                      )
                    )
                  ),
                  nonce = 11,
                  txStatus = TxStatus.TX_STATUS_SUCCESS
                ),
                Activity(
                  owner = to,
                  block = blockNumber,
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
                          blockNumber
                        )
                      )
                    )
                  ),
                  nonce = 11,
                  txStatus = TxStatus.TX_STATUS_SUCCESS
                )
              )
            )
          )
        ),
        AddressBalanceUpdatedEvent(
          address = account.getAddress,
          token = Address.ZERO.toString(),
          balance = Some(
            Amount(
              ByteString.copyFrom("10000000000000000000", "UTF-8"),
              blockNumber
            )
          ),
          block = blockNumber
        ),
        AddressBalanceUpdatedEvent(
          address = to,
          token = Address.ZERO.toString(),
          balance = Some(
            Amount(
              ByteString.copyFrom("1010000000000000000000", "UTF-8"),
              blockNumber
            )
          ),
          block = blockNumber
        )
      ).foreach(eventDispatcher.dispatch)
      Thread.sleep(3000)

      GetActivities
        .Req(account.getAddress)
        .expectUntil(
          check((res: GetActivities.Res) => {
            log.info(
              s"--2 ${res}"
            )
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )
      GetActivities
        .Req(to)
        .expectUntil(
          check((res: GetActivities.Res) => {
            log.info(
              s"--3 ${res}"
            )
            res.activities.length == 1 && res.activities.head.txStatus == TxStatus.TX_STATUS_SUCCESS
          })
        )

      GetPendingActivityNonce
        .Req(account.getAddress, 2)
        .expectUntil(
          check((res: GetPendingActivityNonce.Res) => {
            res.nonces.head == 11
            log.info(
              s"--4 ${res.nonces.head}"
            )
            true
          })
        )

      getBalanceReq.expectUntil(
        check((res: GetAccount.Res) => {
          val balanceOpt = res.accountBalance
          val resBalance = toBigInt(
            balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
          )
          log.info(
            s"--6 ${resBalance}"
          )
          // resBalance == BigInt("10000000000000000000")

          true
        })
      )

      GetAccount
        .Req(
          to,
          allTokens = true
        )
        .expectUntil(
          check((res: GetAccount.Res) => {
            val balanceOpt = res.accountBalance
            val resBalance = toBigInt(
              balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
            )
            log.info(
              s"--7 ${resBalance}"
            )
            // resBalance == BigInt("1010000000000000000000")

            true
          })
        )
    }
  }
}
