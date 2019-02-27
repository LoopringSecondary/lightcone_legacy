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
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import io.lightcone.core._
import io.lightcone.ethereum.persistence._
import io.lightcone.lib.NumericConversion
import io.lightcone.persistence._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._

import scala.concurrent._
import scala.util.{Failure, Success}

class ActivityDalImpl @Inject()(
    val shardId: String,
    val dbConfig: DatabaseConfig[JdbcProfile]
  )(
    implicit
    val ec: ExecutionContext)
    extends ActivityDal {

  val query = TableQuery[ActivityTable]

  def saveActivity(activity: Activity): Future[ErrorCode] =
    db.run(
        (query += activity).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) =>
          ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT
        case Failure(ex) => {
          logger.error(s"error : ${ex.getMessage}")
          ErrorCode.ERR_PERSISTENCE_INTERNAL
        }
        case Success(x) => ErrorCode.ERR_NONE
      }

  def getActivities(
      owner: String,
      token: Option[String],
      paging: Paging
    ): Future[Seq[Activity]] = {
    val filters = createActivityFilters(owner, token)
    db.run(
      filters
        .sortBy(c => c.timestamp.desc)
        .drop(paging.skip)
        .take(paging.size)
        .result
    )
  }

  def countActivities(
      owner: String,
      token: Option[String]
    ): Future[Int] = {
    val filters = createActivityFilters(owner, token)
    db.run(filters.size.result)
  }

  private def createActivityFilters(
      owner: String,
      token: Option[String]
    ) = {
    token match {
      case None => query.filter(_.owner === owner)
      case Some("") =>
        query
          .filter(_.owner === owner)
          .filter(_.token === NumericConversion.toHexString(BigInt(0))) //以0地址表示以太坊
      case Some(value) =>
        query
          .filter(_.owner === owner)
          .filter(_.token === value)
    }
  }

}
