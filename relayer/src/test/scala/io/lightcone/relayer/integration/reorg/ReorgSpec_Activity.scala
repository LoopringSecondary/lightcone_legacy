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

import io.lightcone.ethereum.TxStatus.{TX_STATUS_PENDING, TX_STATUS_SUCCESS}
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.ethereum.persistence.Activity.Detail.EtherTransfer
import io.lightcone.ethereum.persistence.Activity.{ActivityType, Detail}
import io.lightcone.ethereum.persistence.TxEvents.Events.{Activities, Fills}
import io.lightcone.ethereum.persistence._
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

class ReorgSpec_Activity
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test reorg ") {
    scenario("activity in forked block should be set to pending") {

      val txHash1 =
        "0x23c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f"
      implicit val account = getUniqueAccount()
      Given("dispatch an activity")
      val activity = Activity(
        owner = account.getAddress,
        from = account.getAddress,
        activityType = ActivityType.TOKEN_TRANSFER_OUT,
        detail = Activity.Detail.EtherTransfer(
          Activity
            .EtherTransfer(address = account.getAddress, amount = BigInt(10000))
        ),
        txHash = txHash1,
        txStatus = TX_STATUS_SUCCESS,
        block = 100
      )
      val txEvents = TxEvents(
        events = Activities(
          TxEvents.Activities(
            Seq(activity)
          )
        )
      )
      eventDispatcher.dispatch(txEvents)
      Then(" this activity should be saved in db.")

      val res = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(check((res: GetActivities.Res) => res.activities.nonEmpty))

      When("dispatch a block event")
      val blockEvent = BlockEvent(
        blockNumber = 99
      )
      Then(
        "the status of activity should be changed to TX_STATUS_PENDING from db."
      )
      eventDispatcher.dispatch(blockEvent)
      val res1 = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(
          check(
            (res: GetActivities.Res) =>
              res.activities(0).txStatus == TX_STATUS_PENDING
          )
        )

      When("dispatch the activity in a new block")
      val txNewEvents = TxEvents(
        events = Activities(
          TxEvents.Activities(
            Seq(activity.copy(block = 101))
          )
        )
      )
      eventDispatcher.dispatch(txNewEvents)
      Then("check the activity should be saved in db again.")
      val res2 = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(
          check(
            (res: GetActivities.Res) =>
              res.activities(0).txStatus == TX_STATUS_SUCCESS
          )
        )
    }
    scenario("dispatch another activity with the same nonce") {

      val txHash1 =
        "0x23c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f"
      val txHash2 =
        "0x33c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f"
      implicit val account = getUniqueAccount()
      Given("dispatch an activity")
      val activity = Activity(
        owner = account.getAddress,
        from = account.getAddress,
        activityType = ActivityType.TOKEN_TRANSFER_OUT,
        detail = Activity.Detail.EtherTransfer(
          Activity
            .EtherTransfer(address = account.getAddress, amount = BigInt(10000))
        ),
        txHash = txHash1,
        txStatus = TX_STATUS_SUCCESS,
        nonce = 10,
        block = 100
      )
      val txEvents = TxEvents(
        events = Activities(
          TxEvents.Activities(
            Seq(activity)
          )
        )
      )
      eventDispatcher.dispatch(txEvents)
      Then(" this activity should be saved in db.")

      val res = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(check((res: GetActivities.Res) => res.activities.nonEmpty))

      When("dispatch a block event")
      val blockEvent = BlockEvent(
        blockNumber = 99
      )
      Then(
        "the status of activity should be changed to TX_STATUS_PENDING from db."
      )
      eventDispatcher.dispatch(blockEvent)
      val res1 = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(
          check(
            (res: GetActivities.Res) =>
              res.activities(0).txStatus == TX_STATUS_PENDING
          )
        )

      Then("dispatch a new block")
      val newBlockEvent = BlockEvent(
        blockNumber = 101,
        txs = Seq(
          BlockEvent.Tx(
            from = account.getAddress,
            nonce = 10,
            txHash = txHash2
          )
        )
      )
      eventDispatcher.dispatch(newBlockEvent)
      Thread.sleep(1000)

      val res4 = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(
          check(
            (res: GetActivities.Res) => res.activities.isEmpty
          )
        )
      info(s"res4 ${res4}")
      When("dispatch another activity with the same nonce in a new block")
      val txNewEvents = TxEvents(
        events = Activities(
          TxEvents.Activities(
            Seq(
              activity.copy(
                block = 101,
                txHash = txHash2
              )
            )
          )
        )
      )
      eventDispatcher.dispatch(txNewEvents)
      Then("check the new activity should be saved in db again.")
      val res2 = GetActivities
        .Req(
          owner = account.getAddress
        )
        .expectUntil(
          check(
            (res: GetActivities.Res) =>
              res.activities.nonEmpty &&
                res.activities(0).txHash == txHash2
          )
        )

      info(s"#### ${res2}")
      res2.activities.map(_.txHash) shouldNot contain(txHash1)
      res2.activities(0).txStatus should be(TX_STATUS_SUCCESS)
    }
  }
}
