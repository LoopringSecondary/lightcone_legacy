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

import io.lightcone.ethereum.TxStatus.TX_STATUS_SUCCESS
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.persistence.{Activity, TxEvents}
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion.toAmount
import io.lightcone.persistence.CursorPaging
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._
import org.scalatest.matchers.{MatchResult, Matcher}

class EventsSpec_Activity
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test event:Activity") {
    scenario("1: ") {
      implicit val account = getUniqueAccount()

      val blockNumber = 1000

      val getActivityReq = GetActivities
        .Req(
          owner = account.getAddress,
          paging = Some(CursorPaging(size = 10))
        )
      Given("dispatch the first activity with txHash = 0x001 and nonce = 10.")
      val firstActivity = Activity(
        from = account.getAddress,
        owner = account.getAddress,
        txHash = "0x001",
        activityType = Activity.ActivityType.ETHER_TRANSFER_OUT,
        timestamp = timeProvider.getTimeSeconds,
        token = Address.ZERO.toString(),
        detail = Activity.Detail.EtherTransfer(
          Activity.EtherTransfer(
            account.getAddress,
            Some(
              toAmount(BigInt(1000000000))
            )
          )
        ),
        nonce = 10
      )
      eventDispatcher.dispatch(
        TxEvents(
          TxEvents.Events.Activities(
            TxEvents.Activities(
              Seq(firstActivity)
            )
          )
        )
      )
      Given("dispatch the second activity with txHash = 0x002 and nonce = 10.")
      val secondActivity = Activity(
        from = account.getAddress,
        owner = account.getAddress,
        txHash = "0x002",
        activityType = Activity.ActivityType.TOKEN_TRANSFER_OUT,
        timestamp = timeProvider.getTimeSeconds,
        token = Address.ZERO.toString(),
        detail = Activity.Detail.TokenTransfer(
          Activity.TokenTransfer(
            account.getAddress,
            LRC_TOKEN.address,
            Some(
              toAmount(BigInt(1000000000))
            )
          )
        ),
        nonce = 10
      )
      eventDispatcher.dispatch(
        TxEvents(
          TxEvents.Events.Activities(
            TxEvents.Activities(
              Seq(secondActivity)
            )
          )
        )
      )

      getActivityReq
        .expectUntil(
          Matcher { res: GetActivities.Res =>
            MatchResult(
              res.activities
                .exists(_.txHash.equalsIgnoreCase(firstActivity.txHash)),
              "activities doesn't contain the first activites.txHash",
              "activities contains the first activites.txHash"
            )
          } and
            Matcher { res: GetActivities.Res =>
              MatchResult(
                res.activities
                  .exists(_.txHash.equalsIgnoreCase(secondActivity.txHash)),
                "activities doesn't contain the second activites.txHash",
                "activities contains the second activites.txHash"
              )
            }
        )
      Given("dispatch a block event.")
      val blockEvent = BlockEvent(
        blockNumber = blockNumber,
        txs = Seq(
          BlockEvent
            .Tx(account.getAddress, secondActivity.nonce, secondActivity.txHash)
        )
      )
      eventDispatcher.dispatch(blockEvent)

      getActivityReq
        .expectUntil(
          Matcher { res: GetActivities.Res =>
            MatchResult(
              res.activities.isEmpty,
              "res.activities nonEmpty",
              "res.activities is empty"
            )
          }
        )
      Thread.sleep(1000)
      Given("dispatch the blocked second activity.")
      eventDispatcher.dispatch(
        TxEvents(
          TxEvents.Events.Activities(
            TxEvents.Activities(
              Seq(
                secondActivity
                  .copy(block = blockNumber, txStatus = TX_STATUS_SUCCESS)
              )
            )
          )
        )
      )

      Then(" the first activity should be deleted")
      And(" the status of second activity should be TX_STATUS_SUCCESS")

      val activities = getActivityReq
        .expectUntil(
          Matcher { res: GetActivities.Res =>
            MatchResult(
              !res.activities
                .exists(_.txHash.equalsIgnoreCase(firstActivity.txHash)),
              s"activities contains the firstActivity.txHash ${firstActivity.txHash}",
              s"activities doesn't contain the firstActivity.txHash ${firstActivity.txHash}"
            )
          } and
            Matcher { res: GetActivities.Res =>
              val activity = res.activities
                .find(_.txHash.equalsIgnoreCase(secondActivity.txHash))
              MatchResult(
                activity.nonEmpty &&
                  activity.get.txStatus == TX_STATUS_SUCCESS,
                s"activities.size is ${res.activities.size} and the seconActivity.txStatus isn't TX_STATUS_SUCCESS",
                s"activities.size is ${res.activities.size} and the seconActivity.txStatus isn TX_STATUS_SUCCESS"
              )
            }
        )

      info(s"activities ${JsonPrinter.printJsonString(activities)}")

    }
  }

}
