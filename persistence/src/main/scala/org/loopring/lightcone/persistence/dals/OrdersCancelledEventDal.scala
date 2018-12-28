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
import com.typesafe.scalalogging.Logger
import scala.concurrent._
import scala.util.{Failure, Success}

trait OrdersCancelledEventDal
    extends BaseDalImpl[OrdersCancelledEventTable, OrdersCancelledEvent] {

  def saveCancelOrder(cancelOrder: OrdersCancelledEvent): Future[ErrorCode]

  def hasCancelled(orderHash: String): Future[Boolean]

  def obsolete(height: Long): Future[Unit]
}

class OrdersCancelledEventDalImpl(
  )(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends OrdersCancelledEventDal {
  val query = TableQuery[OrdersCancelledEventTable]
  def getRowHash(row: RawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  private[this] val logger = Logger(this.getClass)

  override def saveCancelOrder(
      cancelOrder: OrdersCancelledEvent
    ): Future[ErrorCode] = {
    val now = timeProvider.getTimeMillis
    db.run((query += cancelOrder.copy(createdAt = now)).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
        ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT
      }
      case Failure(ex) ⇒ {
        logger.error(s"error : ${ex.getMessage}")
        ErrorCode.ERR_PERSISTENCE_INTERNAL
      }
      case Success(x) ⇒ ErrorCode.ERR_NONE
    }
  }

  def hasCancelled(orderHash: String): Future[Boolean] = {
    db.run(
        query
          .filter(_.orderHash === orderHash)
          .size
          .result
      )
      .map(_ > 0)
  }

  def obsolete(height: Long): Future[Unit] = {
    db.run(query.filter(_.blockHeight >= height).delete).map(_ >= 0)
  }
}
