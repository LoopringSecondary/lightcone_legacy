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

package io.lightcone.relayer.entrypoint

import com.google.protobuf.ByteString
import io.lightcone.core.Amount
import io.lightcone.relayer.data.GetActivities
import io.lightcone.relayer.support._
import io.lightcone.relayer.validator.ActivityValidator
import scala.concurrent.Await
import akka.pattern._
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.ethereum.persistence.Activity.ActivityType
import io.lightcone.relayer.actors.ActivityActor

class EntryPointSpec_Activity
    extends CommonSpec
    with DatabaseModuleSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with ActivitySupport {

  "save & query some activities" must {
    "save some activities" in {
      def actor = actors.get(ActivityActor.name)
      def validatorActor = actors.get(ActivityValidator.name)
      val owner1 = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val detail1 = Activity.Detail.EtherTransfer(
        Activity.EtherTransfer(
          "0xe7b95e3aefeb28d8a32a46e8c5278721dad39550",
          Some(Amount(ByteString.copyFrom("11", "utf-8")))
        )
      )
      val detail2 = Activity.Detail.OrderCancellation(
        Activity.OrderCancellation(
          Seq("0xe7b95e3aefeb28d8a32a46e8c5278721dad39550"),
          200,
          "LRC-WETH",
          ""
        )
      )
      actor ! Activity(
        owner = owner1,
        block = 1,
        txHash =
          "0x116331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
        activityType = ActivityType.ETHER_TRANSFER_IN,
        detail = detail1,
        sequenceId = 1,
        nonce = 1
      )
      val activity2 = Activity(
        owner = owner1,
        block = 2,
        txHash =
          "0x216331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
        activityType = ActivityType.ORDER_CANCEL,
        detail = detail2,
        sequenceId = 2,
        nonce = 2
      )
      actor ! activity2

      val owner2 = "0x151df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      actor ! activity2.copy(owner = owner2, sequenceId = 3)

      Thread.sleep(1000)
      val r1 = Await.result(
        (validatorActor ? GetActivities.Req(owner1))
          .mapTo[GetActivities.Res],
        timeout.duration
      )
      r1.activities.length should be(2)
      r1.activities.foreach { a =>
        a.owner should be(owner1)
        a.activityType match {
          case ActivityType.ETHER_TRANSFER_IN =>
            a.detail should be(detail1)
          case ActivityType.ORDER_CANCEL =>
            a.detail should be(detail2)
          case _ => assert(false)
        }
      }

      val r2 = Await.result(
        (validatorActor ? GetActivities.Req(owner2))
          .mapTo[GetActivities.Res],
        timeout.duration
      )
      r2.activities.length should be(1)
      val a2 = r2.activities.head
      a2.owner should be(owner2)
      a2.activityType should be(ActivityType.ORDER_CANCEL)
      a2.detail should be(detail2)

      val txs = Seq(
        BlockEvent.Tx(owner1, 1, "0x111"),
        BlockEvent.Tx(owner1, 2, "0x222")
      )
      val shardKeys = ActivityActor.getShardKeys
      shardKeys.foreach { s =>
        val r3 = Await.result(
          (actor ? BlockEvent(100, txs, s)).mapTo[Unit],
          timeout.duration
        )
      }
    }
  }
}
