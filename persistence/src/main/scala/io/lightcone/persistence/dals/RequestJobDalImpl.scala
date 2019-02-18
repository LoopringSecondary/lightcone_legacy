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

package io.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import io.lightcone.core._
import io.lightcone.persistence.RequestJob.JobType
import io.lightcone.persistence._
import io.lightcone.persistence.base.enumColumnType
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._

class RequestJobDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-request-job") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends RequestJobDal {
  import ErrorCode._
  implicit val JobTypeColumnType = enumColumnType(JobType)

  val query = TableQuery[RequestJobTable]

  def saveJob(job: RequestJob): Future[RequestJob] =
    db.run(
      (query returning query
        .map(_.batchId) into ((job, id) => job.copy(batchId = id))) += job.copy(
        batchId = 0
      )
    )

  def updateStatusCode(
      jobId: Int,
      code: Int,
      time: Long
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.batchId === jobId)
          .map(c => (c.statusCode, c.responseTime))
          .update(
            code,
            time
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateSuccessfullyPersisted(jobId: Int): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.batchId === jobId)
          .map(_.persistResult)
          .update(
            true
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def findLatest(jobType: JobType): Future[Option[RequestJob]] =
    db.run(
      query
        .filter(_.jobType === jobType)
        .filter(_.persistResult === true)
        .sortBy(_.batchId.desc)
        .take(1)
        .result
        .headOption
    )
}
