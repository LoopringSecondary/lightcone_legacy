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

import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import com.mysql.jdbc.exceptions.jdbc4._
import scala.concurrent._
import scala.util.{ Failure, Success }

trait CutoffDal
  extends BaseDalImpl[CutoffTable, XCutoff] {

  def saveCutoff(cutoff: XCutoff): Future[XErrorCode]

  def getCutoffs(
    cutoffType: Option[XCutoff.XType] = None,
    cutoffBy: Option[XCutoffBy] = None,
    tradingPair: Option[String] = None,
    sort: Option[XSort] = None,
    skip: Option[XSkip] = None
  ): Future[Seq[XCutoff]]

  def hasCutoffs(
    cutoffType: Option[XCutoff.XType] = None,
    cutoffBy: Option[XCutoffBy] = None,
    tradingPair: Option[String] = None,
    time: Option[Long] // in seconds, where cutoff > time
  ): Future[Boolean]

  def obsolete(height: Long): Future[Unit]
}

class CutoffDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends CutoffDal {
  val query = TableQuery[CutoffTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  implicit val XCutoffCxolumnType = enumColumnType(XCutoff.XType)

  override def saveCutoff(cutoff: XCutoff): Future[XErrorCode] = {
    val now = timeProvider.getTimeMillis
    db.run((query += cutoff.copy(
      createdAt = now,
      isValid = true
    )).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
        XErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT
      }
      case Failure(ex) ⇒ {
        // TODO du: print some log
        // log(s"error : ${ex.getMessage}")
        XErrorCode.ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) ⇒ XErrorCode.ERR_NONE
    }
  }

  override def getCutoffs(
    cutoffType: Option[XCutoff.XType],
    cutoffBy: Option[XCutoffBy] = None,
    tradingPair: Option[String],
    sort: Option[XSort],
    skip: Option[XSkip]
  ): Future[Seq[XCutoff]] = {
    var filters = query.filter(_.isValid === true)
    if (cutoffType.nonEmpty) filters = filters.filter(_.cutoffType === cutoffType.get)
    filters = cutoffBy match {
      case Some(XCutoffBy(XCutoffBy.Value.Broker(value))) ⇒ filters.filter(_.broker === value)
      case Some(XCutoffBy(XCutoffBy.Value.Owner(value))) ⇒ filters.filter(_.owner === value)
      case _ ⇒ filters
    }
    if (tradingPair.nonEmpty) filters = filters.filter(_.tradingPair === tradingPair.get)
    if (sort.nonEmpty) filters = sort.get match {
      case XSort.ASC  ⇒ filters.sortBy(_.createdAt.asc)
      case XSort.DESC ⇒ filters.sortBy(_.createdAt.desc)
      case _          ⇒ filters.sortBy(_.createdAt.asc)
    }
    filters = skip match {
      case Some(s) ⇒ filters.drop(s.skip).take(s.take)
      case None    ⇒ filters
    }
    db.run(filters.result)
  }

  def hasCutoffs(
    cutoffType: Option[XCutoff.XType] = None,
    cutoffBy: Option[XCutoffBy] = None,
    tradingPair: Option[String] = None,
    time: Option[Long]
  ): Future[Boolean] = {
    var filters = query.filter(_.isValid === true)
    if (cutoffType.nonEmpty) filters = filters.filter(_.cutoffType === cutoffType.get)
    filters = cutoffBy match {
      case Some(XCutoffBy(XCutoffBy.Value.Broker(value))) ⇒ filters.filter(_.broker === value)
      case Some(XCutoffBy(XCutoffBy.Value.Owner(value))) ⇒ filters.filter(_.owner === value)
      case _ ⇒ filters
    }
    if (tradingPair.nonEmpty) filters = filters.filter(_.tradingPair === tradingPair.get)
    if (time.nonEmpty) filters = filters.filter(_.cutoff > time.get)
    db.run(filters.size.result).map(_ > 0)
  }

  def obsolete(height: Long): Future[Unit] = {
    val q = for {
      c ← query if c.blockHeight >= height
    } yield (c.isValid, c.updatedAt)
    db.run(q.update(false, timeProvider.getTimeSeconds())).map(_ > 0)
  }
}
