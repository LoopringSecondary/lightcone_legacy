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

import io.lightcone.core.ErrorCode._
import io.lightcone.core.ErrorException
import io.lightcone.core.OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.ethereum.persistence.TxEvents.Events.{Activities, Fills}
import io.lightcone.ethereum.persistence.{Fill, TxEvents}
import io.lightcone.relayer._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.AddedMatchers._
import io.lightcone.relayer.integration.Metadatas._
import org.scalatest._

import scala.math.BigInt

class ReorgSpec_Fill
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test reorg ") {
    scenario("fills in forked block should be deleted") {

      implicit val account = getUniqueAccount()
      Given("dispatch a fill")
      val fill = Fill(
        owner = account.getAddress,
        orderHash =
          "0x03c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
        ringHash =
          "0x13c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
        ringIndex = 10,
        txHash =
          "0x23c90be4c23abe114cbcc5b8cf7a0418ca35a70c0859ffdcfc9c4d26d422a03f",
        amountS = "10".zeros(18),
        amountB = "1".zeros(18),
        blockHeight = 100
      )
      val txEvents = TxEvents(
        events = Fills(
          TxEvents.Fills(
            Seq(fill)
          )
        )
      )
      eventDispatcher.dispatch(txEvents)
      Then("check this fill should be saved in db.")

      val res = GetUserFills
        .Req(
          owner = Some(account.getAddress)
        )
        .expectUntil(check((res: GetUserFills.Res) => res.fills.nonEmpty))

      When("dispatch a block event")
      val blockEvent = BlockEvent(
        blockNumber = 99
      )
      Then("check the fill should be deleted from db.")
      eventDispatcher.dispatch(blockEvent)
      val res1 = GetUserFills
        .Req(
          owner = Some(account.getAddress)
        )
        .expectUntil(check((res: GetUserFills.Res) => res.fills.isEmpty))

      When("dispatch the fill in a new block")
      val txNewEvents = TxEvents(
        events = Fills(
          TxEvents.Fills(
            Seq(fill.copy(blockHeight = 101))
          )
        )
      )
      eventDispatcher.dispatch(txNewEvents)
      Then("check the fill should be saved in db again.")
      val res2 = GetUserFills
        .Req(
          owner = Some(account.getAddress)
        )
        .expectUntil(check((res: GetUserFills.Res) => res.fills.nonEmpty))

    }
  }
}
