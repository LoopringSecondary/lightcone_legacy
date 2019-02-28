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
import io.lightcone.persistence.{Activity, CursorPaging}
import io.lightcone.persistence.Activity.ActivityType
import io.lightcone.relayer.data.GetAccountActivities
import io.lightcone.relayer.support._
import io.lightcone.relayer.validator.ActivityValidator
import scala.concurrent.Await
import akka.pattern._

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
      def actor = actors.get(ActivityValidator.name)
      val owner1 = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val detail = Activity.Detail.EtherTransfer(
        Activity.EtherTransfer(
          "0xe7b95e3aefeb28d8a32a46e8c5278721dad39550",
          Some(Amount(ByteString.copyFrom("11", "utf-8")))
        )
      )
      actor ! Activity(
        owner = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        block = 1,
        txHash =
          "0x016331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4",
        activityType = ActivityType.ETHER_TRANSFER_IN,
        detail = detail,
        sequenceId = 1
      )
      Thread.sleep(1000)
      val r1 = Await.result(
        (actor ? GetAccountActivities.Req(owner1))
          .mapTo[GetAccountActivities.Res],
        timeout.duration
      )
      r1.activities.nonEmpty should be(true)
      r1.activities.length should be(1)
      val activity = r1.activities.head
      activity.owner should be(owner1)
      activity.activityType should be(ActivityType.ETHER_TRANSFER_IN)
      activity.detail should be(detail)
    }
  }
}
