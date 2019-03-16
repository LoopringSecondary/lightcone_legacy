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
import io.lightcone.ethereum.persistence._
import io.lightcone.persistence._
import io.lightcone.lib._
import slick.basic._
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

class RingDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-ring") val dbConfig: DatabaseConfig[JdbcProfile],
    timeProvider: TimeProvider)
    extends RingDal {

  val query = TableQuery[RingTable]

  def saveRing(ring: Ring): Future[ErrorCode] = {
    db.run((query += ring).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        ErrorCode.ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) => ErrorCode.ERR_NONE
    }
  }

  def saveRings(rings: Seq[Ring]): Future[Seq[ErrorCode]] =
    Future.sequence(rings.map(saveRing))

  private def queryFilters(
      ringHashOpt: Option[String],
      ringIndexOpt: Option[Long],
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[RingTable, RingTable#TableElementType, Seq] = {
    var filters = query.filter(_.ringIndex >= 0L)
    filters = ringHashOpt match {
      case None    => filters
      case Some(h) => filters.filter(_.ringHash === h)
    }
    filters = ringIndexOpt match {
      case None    => filters
      case Some(h) => filters.filter(_.ringIndex === h)
    }
    filters = sort match {
      case Some(s) if s == SortingType.DESC =>
        filters.sortBy(_.ringIndex.desc)
      case _ => filters.sortBy(_.ringIndex.asc)
    }
    filters = pagingOpt match {
      case Some(paging) => filters.drop(paging.skip).take(paging.size)
      case None         => filters
    }
    filters
  }

  def getRings(
      ringHashOpt: Option[String],
      ringIndexOpt: Option[Long],
      sort: SortingType,
      paging: Option[Paging]
    ): Future[Seq[Ring]] = {
    val filters =
      queryFilters(ringHashOpt, ringIndexOpt, Some(sort), paging)
    db.run(filters.result)
  }

  def countRings(
      ringHashOpt: Option[String],
      ringIndexOpt: Option[Long]
    ): Future[Int] = {
    val filters = queryFilters(ringHashOpt, ringIndexOpt, None, None)
    db.run(filters.size.result)
  }
}
