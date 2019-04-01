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
import io.lightcone.core.ErrorCode._
import io.lightcone.persistence.TokenTickerRecord
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._
import scala.util.{Failure, Success}

class TokenTickerRecordDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-ticker-record") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends TokenTickerRecordDal {

  val query = TableQuery[TokenTickerRecordTable]

  def saveTickers(tickers: Seq[TokenTickerRecord]) =
    db.run((query ++= tickers).asTry).map {
      case Failure(ex) => {
        logger.error(s"save tickers error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) =>
        ERR_NONE
    }

  def getLastTickerTime() = {
    db.run(query.filter(_.isValid === true).map(_.timestamp).max.result)
  }

  def getTickers(timestamp: Long): Future[Seq[TokenTickerRecord]] =
    db.run(query.filter(_.timestamp === timestamp).result)

  def countTickers(timestamp: Long) =
    db.run(query.filter(_.timestamp === timestamp).size.result)

  def setValid(timestamp: Long) =
    for {
      result <- db.run(
        query
          .filter(_.timestamp === timestamp)
          .map(_.isValid)
          .update(true)
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }
}
