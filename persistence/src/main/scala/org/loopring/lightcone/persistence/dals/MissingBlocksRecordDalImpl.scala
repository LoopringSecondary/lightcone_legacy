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

package org.loopring.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.MissingBlocksRecord
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent.{ExecutionContext, Future}

class MissingBlocksRecordDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-missing-blocks-record") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends MissingBlocksRecordDal {
  val query = TableQuery[MissingBlocksRecordTable]

  def saveMissingBlock(record: MissingBlocksRecord): Future[Long] =
    db.run(
      query returning query.map(_.sequenceId) += record.copy(sequenceId = 0)
    )

  def getOldestOne(): Future[Option[MissingBlocksRecord]] =
    db.run(query.sortBy(_.sequenceId.asc).take(1).result.headOption)

  def updateProgress(
      sequenceId: Long,
      progressTo: Long
    ): Future[Int] =
    db.run(
      query
        .filter(_.sequenceId === sequenceId)
        .map(c => c.lastHandledBlock)
        .update(progressTo)
    )

  def deleteRecord(sequenceId: Long): Future[Boolean] =
    db.run(query.filter(_.sequenceId === sequenceId).delete).map(_ > 0)
}
