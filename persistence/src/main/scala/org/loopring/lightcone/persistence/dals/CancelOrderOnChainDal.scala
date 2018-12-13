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

trait CancelOrderOnChainDal
  extends BaseDalImpl[CancelOrderOnChainTable, XCancelOrderOnChain] {

  def saveCancelOrder(cancelOrder: XCancelOrderOnChain): Future[XErrorCode]

  def hasCancelled(
    orderHash: String
  ): Future[Boolean]

  def obsolete(height: Long): Future[Unit]
}

class CancelOrderOnChainDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends CancelOrderOnChainDal {
  val query = TableQuery[CancelOrderOnChainTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()

  override def saveCancelOrder(cancelOrder: XCancelOrderOnChain): Future[XErrorCode] = {
    val now = timeProvider.getTimeMillis
    db.run((query += cancelOrder.copy(
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

  def hasCancelled(
    orderHash: String
  ): Future[Boolean] = {
    db.run(query
      .filter(_.orderHash === orderHash)
      .filter(_.isValid === true)
      .size
      .result).map(_ > 0)
  }

  def obsolete(height: Long): Future[Unit] = {
    val q = for {
      c ← query if c.blockHeight >= height
    } yield (c.isValid, c.updatedAt)
    db.run(q.update(false, timeProvider.getTimeSeconds())).map(_ > 0)
  }
}
