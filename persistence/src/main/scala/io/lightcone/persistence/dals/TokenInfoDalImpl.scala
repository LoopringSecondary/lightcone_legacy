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
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import io.lightcone.core._
import io.lightcone.lib.TimeProvider
import org.slf4s.Logging
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import scala.concurrent._
import scala.util.{Failure, Success}

class TokenInfoDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-token-info") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    timeProvider: TimeProvider)
    extends TokenInfoDal
    with Logging {

  import ErrorCode._

  val query = TableQuery[TokenInfoTable]

  def saveTokenInfo(token: TokenInfo): Future[ErrorCode] =
    db.run((query += token).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        ERR_PERSISTENCE_DUPLICATE_INSERT
      case Failure(ex) =>
        logger.error(s"error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      case Success(x) => ERR_NONE
    }

  def saveTokenInfos(tokens: Seq[TokenInfo]): Future[Seq[String]] =
    for {
      _ <- Future.sequence(tokens.map(saveTokenInfo))
      query <- getTokenInfos(tokens.map(_.symbol))
    } yield query.map(_.symbol)

  def updateTokenInfo(token: TokenInfo): Future[ErrorCode] =
    for {
      result <- db.run(query.insertOrUpdate(token))
    } yield {
      if (result == 1) {
        ERR_NONE
      } else {
        ERR_PERSISTENCE_INTERNAL
      }
    }

  def getTokenInfos(symbols: Seq[String]): Future[Seq[TokenInfo]] =
    db.run(query.filter(_.symbol inSet symbols).result)

  def getTokenInfos(): Future[Seq[TokenInfo]] =
    db.run(query.take(Int.MaxValue).result)
}
