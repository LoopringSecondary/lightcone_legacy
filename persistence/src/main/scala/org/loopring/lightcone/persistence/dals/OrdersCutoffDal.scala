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
import scala.util.{Failure, Success}

trait OrdersCutoffDal
    extends BaseDalImpl[OrdersCutoffTable, XOrdersCutoffEvent] {

  def saveCutoff(cutoff: XOrdersCutoffEvent): Future[XErrorCode]

  def hasCutoff(
      orderBroker: Option[String] = None,
      orderOwner: String,
      orderTradingPair: String,
      time: Long // in seconds, where cutoff > time
    ): Future[Boolean]

  def obsolete(height: Long): Future[Unit]
}

class OrdersCutoffDalImpl(
  )(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends OrdersCutoffDal {
  val query = TableQuery[OrdersCutoffTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()

  override def saveCutoff(cutoff: XOrdersCutoffEvent): Future[XErrorCode] = {
    val now = timeProvider.getTimeMillis
    db.run(
        (query += cutoff.copy(
          createdAt = now
        )).asTry
      )
      .map {
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

  def hasCutoff(
      orderBroker: Option[String] = None,
      orderOwner: String,
      orderTradingPair: String,
      time: Long
    ): Future[Boolean] = {
    val filters = query.filter(_.createdAt > 0L)
    if (orderBroker.nonEmpty) {
      val q1 = filters
        .filter(_.broker === orderBroker.get)
        .filter(_.owner === "")
        .filter(_.tradingPair === "")
        .filter(_.cutoff > time) // broker cancel all
      val q2 = filters
        .filter(_.broker === orderBroker.get)
        .filter(_.owner === orderOwner)
        .filter(_.tradingPair === "")
        .filter(_.cutoff > time) // broker cancel owner's all
      val q3 = filters
        .filter(_.broker === orderBroker.get)
        .filter(_.owner === "")
        .filter(_.tradingPair === orderTradingPair)
        .filter(_.cutoff > time) // broker cancel market's all
      val q4 = filters
        .filter(_.broker === orderBroker.get)
        .filter(_.owner === orderOwner)
        .filter(_.tradingPair === orderTradingPair)
        .filter(_.cutoff > time) // broker cancel owner and market's all
      db.run((q1 union q2 union q3 union q4).size.result).map(_ > 0)
    } else {
      val q1 = filters
        .filter(_.broker === "")
        .filter(_.owner === orderOwner)
        .filter(_.tradingPair === "")
        .filter(_.cutoff > time) // owner cancel all market
      val q2 = filters
        .filter(_.broker === "")
        .filter(_.owner === orderOwner)
        .filter(_.tradingPair === orderTradingPair)
        .filter(_.cutoff > time) // owner cancel market's all
      db.run((q1 union q2).size.result).map(_ > 0)
    }
  }

  def obsolete(height: Long): Future[Unit] = {
    db.run(query.filter(_.blockHeight >= height).delete).map(_ >= 0)
  }
}
