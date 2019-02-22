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
import io.lightcone.core.ErrorCode
import io.lightcone.core.ErrorCode._
import io.lightcone.persistence._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._
import scala.util.{Failure, Success}

class CMCTickerConfigDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-cmc-ticker-config") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends CMCTickerConfigDal {

  val query = TableQuery[CMCTickerConfigTable]

  def saveSlugs(slugs: Seq[CMCTickerConfig]): Future[ErrorCode] =
    db.run((query ++= slugs).asTry).map {
      case Failure(ex) => {
        logger.error(s"save tickers error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) =>
        ERR_NONE
    }

  def getAll(): Future[Seq[CMCTickerConfig]] =
    db.run(query.take(Integer.MAX_VALUE).result)

  def getBySlugs(slugs: Seq[String]): Future[Seq[CMCTickerConfig]] =
    db.run(query.filter(_.slug inSet slugs).result)

  def update(ticker: CMCTickerConfig): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.symbol === ticker.symbol)
          .map(_.slug)
          .update(ticker.symbol)
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def deleteBySymbol(symbol: String): Future[Boolean] =
    db.run(query.filter(_.symbol === symbol).delete).map(_ > 0)
}
