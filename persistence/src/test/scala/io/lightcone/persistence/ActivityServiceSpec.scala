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

package io.lightcone.persistence

import io.lightcone.core._
import io.lightcone.persistence.Activity.ActivityType
import io.lightcone.persistence.dals._

import scala.concurrent._
import scala.concurrent.duration._

class ActivityServiceSpec extends ServiceSpec[ActivityService] {

  implicit var dal: ActivityDal = _

  def getService = {
    dal = new ActivityDalImpl("1", dbConfig)
    new ActivityServiceImpl()
  }

  def createTables(): Unit = dal.createTable()

  "activityService" must "success to save and query activities" in {
    info("save several activities")
    val activities = Seq(
      Activity(
        owner = "0xc88762dea88c2834b3cdb22bc0975c62f9ea2998",
        token = "0xb88762dea88c2834b3cdb22bc0975c62f9ea2998",
        block = 100,
        txHash = "0xc88762dea88c2834b3cdb22bc0975c62f9ea299811111",
        activityType = ActivityType.TOKEN_TRANSFER_OUT,
        timestamp = timeProvider.getTimeSeconds(),
        detail = Activity.Detail.TokenTransfer(
          Activity.TokenTransfer(
            address = "0xc88762dea88c2834b3cdb22bc0975c62f9ea2998",
            token = "0xb88762dea88c2834b3cdb22bc0975c62f9ea2998",
            amount = BigInt(10000)
          )
        )
      ),
      Activity(
        owner = "0xc88762dea88c2834b3cdb22bc0975c62f9ea2998",
        token = "0xb98762dea88c2834b3cdb22bc0975c62f9ea2998",
        block = 100,
        txHash = "0xc88762dea88c2834b3cdb22bc0975c62f9ea299811111",
        activityType = ActivityType.TOKEN_TRANSFER_OUT,
        timestamp = timeProvider.getTimeSeconds(),
        detail = Activity.Detail.TokenTransfer(
          Activity.TokenTransfer(
            address = "0xc88762dea88c2834b3cdb22bc0975c62f9ea2998",
            token = "0xb88762dea88c2834b3cdb22bc0975c62f9ea2998",
            amount = BigInt(10000)
          )
        )
      )
    )

    val saveF = Future.sequence {
      activities.map { activity =>
        service.saveActivity(activity)
      }
    }
    Await.result(saveF, 5.second)

    info("query activities")

    val queryF = service.getActivities(
      "0xc88762dea88c2834b3cdb22bc0975c62f9ea2998",
      Some("0xb88762dea88c2834b3cdb22bc0975c62f9ea2998"),
      CursorPaging(0, 4)
    )
    val queriedActivities = Await.result(queryF, 5.second)
    queriedActivities._2 should be(1)
  }
}
