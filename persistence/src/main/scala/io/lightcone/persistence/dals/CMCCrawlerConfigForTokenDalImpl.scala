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

class CMCCrawlerConfigForTokenDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-cmc-ticker-config") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends CMCCrawlerConfigForTokenDal {

  val query = TableQuery[CMCCrawlerConfigForTokenTable]

  def saveConfigs(configs: Seq[CMCCrawlerConfigForToken]): Future[ErrorCode] =
    db.run((query ++= configs).asTry).map {
      case Failure(ex) => {
        logger.error(s"save ticker configs error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) =>
        ERR_NONE
    }

  def getConfigs(): Future[Seq[CMCCrawlerConfigForToken]] =
    db.run(query.take(Integer.MAX_VALUE).result)

  def getConfigs(slugs: Seq[String]): Future[Seq[CMCCrawlerConfigForToken]] =
    db.run(query.filter(_.slug inSet slugs).result)

  def updateConfig(ticker: CMCCrawlerConfigForToken): Future[ErrorCode] =
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

  def deleteConfigs(symbol: String): Future[Boolean] =
    db.run(query.filter(_.symbol === symbol).delete).map(_ > 0)
}
