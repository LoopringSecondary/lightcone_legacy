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

import io.lightcone.persistence.RequestJob.JobType
import io.lightcone.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class RequestJobTable(tag: Tag)
    extends BaseTable[RequestJob](tag, "T_REQUEST_JOB") {
  implicit val JobTypeColumnType = enumColumnType(JobType)

  def id = batchId.toString()
  def batchId = column[Int]("batch_id", O.PrimaryKey, O.AutoInc)
  def jobType = column[JobType]("job_type")
  def requestTime = column[Long]("request_time")
  def responseTime = column[Long]("response_time")
  def statusCode = column[Int]("status_code")
  def persistResult = column[Boolean]("persist_result")

  def idx_job_result_batch =
    index(
      "idx_job_result_batch",
      (jobType, persistResult, batchId),
      unique = false
    )

  def * =
    (
      batchId,
      jobType,
      requestTime,
      responseTime,
      statusCode,
      persistResult
    ) <> ((RequestJob.apply _).tupled, RequestJob.unapply)
}
