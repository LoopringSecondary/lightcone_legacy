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

import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.tables.OHLCDataTable
import org.loopring.lightcone.proto.ErrorCode.{
  ERR_NONE,
  ERR_PERSISTENCE_INTERNAL
}
import slick.basic.DatabaseConfig
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}
import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.proto._
import org.postgresql.util.PSQLException
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}

import scala.util.{Failure, Success}

class OHLCDataDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-ohlc-data") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends OHLCDataDal {

  val query = TableQuery[OHLCDataTable]

  def saveRawData(record: RawData): Future[PersistRawData.Res] = {
    db.run((query += record).asTry).map {
      case Success(x) =>
        PersistRawData.Res(error = ERR_NONE, record = Some(record))
      // unique key violation
      case Failure(e: PSQLException if(e.getSQLState == "23505") => {
        logger.warn(s"warn : ${e.getMessage}")
        PersistRawData.Res(error = ERR_NONE, record = Some(record))
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        PersistRawData.Res(error = ERR_PERSISTENCE_INTERNAL, record = None)
      }

    }
  }

  def getOHLCData(
      marketId: String,
      interval: String,
      beginTime: Long,
      endTime: Long
    ): Future[GetOHLCData.Res] = {
    // query result set getters
    implicit val getOHLCResult = GetResult[OHLCData](
      r =>
        OHLCData(
          r.nextString,
          r.nextString,
          r.nextDouble,
          r.nextDouble,
          r.nextDouble,
          r.nextDouble,
          r.nextDouble
        )
    )
    val tableName = "T_OHLC_DATA"
    val sql =
      sql"""select time_bucket($interval, time) as time_flag
          t.market_id,
          MAX(price) as highest_price,
          MIN(price) as lowest_price,
          SUM(volume) as volume,
          first(price, time) as opening_price,
          last(price, time) as closing_price
         from $tableName t where market_id = marketId
         and time < $beginTime and time > $endTime GROUP BY time_flag"""
        .as[OHLCData]
    db.run(sql).map(r => GetOHLCData.Res(r.toSeq))

  }
}
