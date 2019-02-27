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

import com.google.inject.Inject
import io.lightcone.core._
import io.lightcone.persistence.dals._

import scala.concurrent.{ExecutionContext, Future}

class ActivityServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    activityDal: ActivityDal)
    extends ActivityService {

  def saveActivity(activity: Activity): Future[ErrorCode] =
    activityDal.saveActivity(activity)

  def getActivities(
      owner: String,
      token: Option[String],
      paging: Paging
    ): Future[(Seq[Activity], Int)] =
    for {
      activities <- activityDal.getActivities(owner, token, paging)
      total <- activityDal.countActivities(owner, token)
    } yield (activities, total)

}
