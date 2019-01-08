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

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import org.loopring.lightcone.persistence.tables.BlockchainScanRecordTable
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.util.{Failure, Success}

trait BlockchainScanRecordDal
    extends BaseDalImpl[BlockchainScanRecordTable, BlockchainRecordData] {

  def saveRecord(
      record: BlockchainRecordData
    ): Future[PersistBlockchainRecord.Res]

  def getRecordsByOwner(
      owner: String,
      sort: SortingType,
      paging: CursorPaging
    ): Future[Seq[BlockchainRecordData]]
}

class BlockchainScanRecordDalImpl(
    tableIndex: Int
  )(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val config: Config,
    val ec: ExecutionContext)
    extends BlockchainScanRecordDal {
  val query = TableQuery(new BlockchainScanRecordTable(tableIndex)(_))
  def getRowHash(row: RawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  private[this] val logger = Logger(this.getClass)

  def saveRecord(
      record: BlockchainRecordData
    ): Future[PersistBlockchainRecord.Res] = {
    db.run((query += record).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) => {
        PersistBlockchainRecord.Res(
          error = ERR_PERSISTENCE_DUPLICATE_INSERT,
          data = None,
          alreadyExist = true
        )
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        PersistBlockchainRecord
          .Res(error = ERR_PERSISTENCE_INTERNAL, data = None)
      }
      case Success(x) =>
        PersistBlockchainRecord.Res(error = ERR_NONE, data = Some(record))
    }
  }

  def getRecordsByOwner(
      owner: String,
      sort: SortingType,
      paging: CursorPaging
    ): Future[Seq[BlockchainRecordData]] = {
    var filters = query
      .filter(_.owner === owner)
    filters = if (sort == SortingType.ASC) {
      filters.sortBy(c => (c.blockNumber.asc, c.txIndex.asc, c.logIndex.asc))
    } else {
      filters.sortBy(c => (c.blockNumber.desc, c.txIndex.desc, c.logIndex.desc))
    }
    //TODO 改成blockNumber  txIndex logIndex
//    if (paging.cursor > 0) {
//      filters = filters.filter(_.sequenceId > paging.cursor)
//    }
    if (paging.size > 0) {
      filters = filters.take(paging.size)
    }
    db.run(filters.result)
  }
}
