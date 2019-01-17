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
import com.google.protobuf.any.Any
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
    ])
    extends OHLCDataDal {

  val query = TableQuery[OHLCDataTable]

  def saveData(record: OHLCRawData): Future[PersistRawData.Res] = {
    insertOrUpdate(record).map {
      case 0 =>
        logger.error("saving OHLC raw data failed")
        PersistRawData.Res(error = ERR_PERSISTENCE_INTERNAL, record = None)
      case _ => {
        PersistRawData.Res(error = ERR_NONE, record = Some(record))
      }

    }
  }

  def getOHLCData(
      marketId: String,
      interval: Long,
      beginTime: Long,
      endTime: Long
    ): Future[GetOHLCData.Res] = {
    // query result set getters
    implicit val getOHLCResult = GetResult[OHLCData](
      r =>
        OHLCData(
          r.nextInt,
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
      sql"""select
        time_bucket($interval, time) as starting_point
        t.market_id,
        MAX(price) as highest_price,
        MIN(price) as lowest_price,
        SUM(volume_a) as volume_a_sum,
        SUM(volume_b) as volume_b_sum,
        first(price, time) as opening_price,
        last(price, time) as closing_price
        from $tableName t where market_key = marketKey
        and time < $beginTime and time > $endTime GROUP BY time_flag"""
        .as[OHLCData]
    db.run(sql).map(r => GetOHLCData.Res(r.toSeq))

  }
}
