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

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

trait SubmitTxDal extends BaseDalImpl[SubmitTxTable, XSubmitTx] {
  def saveTx(tx: XSubmitTx): Future[XErrorCode]
  // get all pending txs with given owner, from_nonce is a optional parameter(>=)
  def getPendingTxs(request: XGetPendingTxsReq): Future[Seq[XSubmitTx]]
  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(request: XUpdateTxInBlockReq): Future[XErrorCode]
}

class SubmitTxDalImpl(
  )(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends SubmitTxDal {
  private[this] val logger = Logger(this.getClass)
  val query = TableQuery[SubmitTxTable]
  val timeProvider = new SystemTimeProvider()
  implicit val XStatusCxolumnType = enumColumnType(XSubmitTx.XStatus)

  def saveTx(tx: XSubmitTx): Future[XErrorCode] = {
    db.run(
        (query += tx).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) ⇒
          ERR_PERSISTENCE_DUPLICATE_INSERT
        case Failure(ex) ⇒
          logger.error(s"error : ${ex.getMessage}")
          ERR_PERSISTENCE_INTERNAL
        case Success(x) ⇒ ERR_NONE
      }
  }

  //TODO du: 过滤相同的from,nonce的记录
  def getPendingTxs(request: XGetPendingTxsReq): Future[Seq[XSubmitTx]] = {
    val status: XSubmitTx.XStatus = XSubmitTx.XStatus.PENDING
    val q = query
      .filter(_.from === request.owner)
      .filter(_.createAt <= request.timeBefore)
      .filter(_.status === status)
      .result
    db.run(q)
  }

  def updateInBlock(request: XUpdateTxInBlockReq): Future[XErrorCode] = {
    val status: XSubmitTx.XStatus = XSubmitTx.XStatus.BLOCK
    db.run(
        query
          .filter(_.from === request.from)
          .filter(_.nonce === request.nonce)
          .map(_.status)
          .update(status)
      )
      .map { r =>
        if (r > 0) XErrorCode.ERR_NONE
        else XErrorCode.ERR_INTERNAL_UNKNOWN
      }
  }
}
