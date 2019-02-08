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

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import io.lightcone.persistence.base._
import io.lightcone.persistence._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import io.lightcone.core.ErrorCode._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.util.{Failure, Success}

class TransactionRecordDalImpl(
    val shardId: String,
    val dbConfig: DatabaseConfig[JdbcProfile]
  )(
    implicit
    val ec: ExecutionContext)
    extends TransactionRecordDal {

  implicit val txStatusColumnType = enumColumnType(TxStatus)
  implicit val recordTypeColumnType = enumColumnType(
    TransactionRecord.RecordType
  )
  implicit val dataColumnType = eventDataColumnType()

  val query = TableQuery(new TransactionRecordTable(shardId)(_))

  def getRowHash(row: RawOrder) = row.hash

  def saveRecord(
      record: TransactionRecord
    ): Future[PersistTransactionRecord.Res] = {
    db.run((query += record).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) => {
        PersistTransactionRecord
          .Res(error = ERR_PERSISTENCE_DUPLICATE_INSERT, alreadyExist = true)
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        PersistTransactionRecord
          .Res(error = ERR_PERSISTENCE_INTERNAL)
      }
      case Success(x) =>
        PersistTransactionRecord.Res(error = ERR_NONE)
    }
  }

  def getRecordsByOwner(
      owner: String,
      queryType: Option[GetTransactionRecords.QueryType],
      sort: SortingType,
      paging: CursorPaging
    ): Future[Seq[TransactionRecord]] = {
    var filters = query
      .filter(_.owner === owner)
    if (queryType.nonEmpty) {
      filters = filters.filter(_.recordType === queryType.get.value)
    }
    if (paging.cursor > 0) {
      filters = filters.filter(_.sequenceId > paging.cursor)
    }
    filters = if (sort == SortingType.ASC) {
      filters.sortBy(_.sequenceId)
    } else {
      filters.sortBy(_.sequenceId.desc)
    }
    if (paging.size > 0) {
      filters = filters.take(paging.size)
    }
    db.run(filters.result)
  }

  def getRecordsCountByOwner(
      owner: String,
      queryType: Option[GetTransactionRecords.QueryType]
    ): Future[Int] = {
    var filters = query
      .filter(_.owner === owner)
    if (queryType.nonEmpty) {
      filters = filters.filter(_.recordType === queryType.get.value)
    }
    db.run(filters.size.result)
  }
}
