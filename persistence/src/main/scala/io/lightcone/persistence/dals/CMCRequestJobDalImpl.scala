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
import io.lightcone.persistence._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._

class CMCRequestJobDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-cmc-request-job") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends CMCRequestJobDal {
  import ErrorCode._

  val query = TableQuery[CMCRequestJobTable]

  def saveJob(job: CMCRequestJob): Future[CMCRequestJob] =
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

  def findLatest(): Future[Option[CMCRequestJob]] =
    db.run(
      query
        .filter(_.persistResult === true)
        .sortBy(_.batchId.desc)
        .take(1)
        .result
        .headOption
    )
}
