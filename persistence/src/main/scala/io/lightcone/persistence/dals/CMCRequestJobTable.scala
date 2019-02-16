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

import io.lightcone.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class CMCRequestJobTable(tag: Tag)
    extends BaseTable[CMCRequestJob](tag, "T_CMC_REQUEST_JOB") {

  def id = batchId.toString()
  def batchId = column[Int]("batch_id", O.PrimaryKey, O.AutoInc)
  def requestTime = column[Long]("request_time")
  def responseTime = column[Long]("response_time")
  def statusCode = column[Int]("status_code")
  def persistResult = column[Boolean]("persist_result")

  def * =
    (
      batchId,
      requestTime,
      responseTime,
      statusCode,
      persistResult
    ) <> ((CMCRequestJob.apply _).tupled, CMCRequestJob.unapply)
}
