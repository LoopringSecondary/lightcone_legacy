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

import com.google.inject.name.Named
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import org.slf4s.Logging
import com.google.inject.Inject
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.base.enumColumnType
import scala.util.{Failure, Success}

class MarketMetadataDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-market-metadata") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends MarketMetadataDal
    with Logging {
  val query = TableQuery[MarketMetadataTable]
  implicit val statusColumnType = enumColumnType(MarketMetadata.Status)

  def saveMarket(marketMetadata: MarketMetadata): Future[ErrorCode] =
    db.run((query += marketMetadata).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        ERR_PERSISTENCE_DUPLICATE_INSERT
      case Failure(ex) =>
        logger.error(s"error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      case Success(x) => ERR_NONE
    }

  def saveMarkets(marketMetadatas: Seq[MarketMetadata]): Future[Seq[String]] =
    for {
      _ <- Future.sequence(marketMetadatas.map(saveMarket))
      query <- getMarketsByKey(marketMetadatas.map(_.marketKey))
    } yield query.map(_.marketKey)

  def updateMarket(marketMetadata: MarketMetadata): Future[ErrorCode] =
    for {
      result <- db.run(query.insertOrUpdate(marketMetadata))
    } yield {
      if (result == 1) {
        ERR_NONE
      } else {
        ERR_PERSISTENCE_INTERNAL
      }
    }

  def getMarkets(): Future[Seq[MarketMetadata]] =
    db.run(query.result)

  def getMarketsByKey(marketKeyes: Seq[String]): Future[Seq[MarketMetadata]] =
    db.run(query.filter(_.marketKey inSet marketKeyes).result)

  def disableMarketByHash(marketKey: String): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.marketKey === marketKey)
          .map(c => (c.status, c.updateAt))
          .update(MarketMetadata.Status.DISABLED, timeProvider.getTimeMillis())
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }
}
