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
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.tables.DealtRecordTable
import org.loopring.lightcone.proto.ErrorCode.{
  ERR_NONE,
  ERR_PERSISTENCE_DUPLICATE_INSERT,
  ERR_PERSISTENCE_INTERNAL
}
import slick.basic.DatabaseConfig
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.proto._

import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{JdbcProfile}
import scala.util.{Failure, Success}

class DealtRecordDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-dealt-record") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends DealtRecordDal {

  val query = TableQuery[DealtRecordTable]

  def saveDealtRecord(record: DealtRecord): Future[PersistDealtRecord.Res] = {
    db.run((query += record).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) => {
        PersistDealtRecord.Res(
          error = ERR_PERSISTENCE_DUPLICATE_INSERT,
          record = None,
          alreadyExist = true
        )
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        PersistDealtRecord.Res(error = ERR_PERSISTENCE_INTERNAL, record = None)
      }
      case Success(x) =>
        PersistDealtRecord.Res(error = ERR_NONE, record = Some(record))
    }
  }
}
