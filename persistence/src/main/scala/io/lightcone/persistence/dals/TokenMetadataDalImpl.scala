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

import com.google.inject.name.Named
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._
import org.slf4s.Logging
import com.google.inject.Inject
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence.base.enumColumnType
import scala.util.{Failure, Success}

class TokenMetadataDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-metadata") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends TokenMetadataDal
    with Logging {

  import ErrorCode._

  val query = TableQuery[TokenMetadataTable]
  implicit val statusColumnType = enumColumnType(TokenMetadata.Status)

  def saveToken(tokenMetadata: TokenMetadata): Future[ErrorCode] =
    db.run((query += tokenMetadata).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        ERR_PERSISTENCE_DUPLICATE_INSERT
      case Failure(ex) =>
        logger.error(s"error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      case Success(x) => ERR_NONE
    }

  def saveTokens(tokenMetadatas: Seq[TokenMetadata]): Future[Seq[String]] =
    for {
      _ <- Future.sequence(tokenMetadatas.map(saveToken))
      query <- getTokens(tokenMetadatas.map(_.address))
    } yield query.map(_.address)

  def updateToken(tokenMetadata: TokenMetadata): Future[ErrorCode] =
    for {
      result <- db.run(query.insertOrUpdate(tokenMetadata))
    } yield {
      if (result == 1) {
        ERR_NONE
      } else {
        ERR_PERSISTENCE_INTERNAL
      }
    }

  def updateTokenPrice(
      token: String,
      usdPrice: Double
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.address === token)
          .map(c => (c.usdPrice, c.updateAt))
          .update(
            usdPrice,
            timeProvider.getTimeMillis()
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def getTokens() =
    db.run(query.take(Int.MaxValue).result)

  def getTokens(addresses: Seq[String]): Future[Seq[TokenMetadata]] =
    db.run(query.filter(_.address inSet addresses).result)

  def updateBurnRate(
      token: String,
      burnRateForMarket: Double,
      burnRateForP2P: Double
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.address === token)
          .map(c => (c.burnRateForMarket, c.burnRateForP2P, c.updateAt))
          .update(
            burnRateForMarket,
            burnRateForP2P,
            timeProvider.getTimeMillis()
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def InvalidateToken(address: String): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.address === address)
          .map(c => (c.status, c.updateAt))
          .update(TokenMetadata.Status.INVALID, timeProvider.getTimeMillis())
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }
}
