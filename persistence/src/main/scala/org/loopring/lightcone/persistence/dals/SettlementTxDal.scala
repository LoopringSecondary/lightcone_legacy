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
import org.loopring.lightcone.lib.{ErrorException, SystemTimeProvider}
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

trait SettlementTxDal extends BaseDalImpl[SettlementTxTable, XSettlementTx] {
  def saveTx(tx: XSettlementTx): Future[XSaveSettlementTxResult]
  // get all pending txs with given owner, from_nonce is a optional parameter(>=)
  def getPendingTxs(request: XGetPendingTxsReq): Future[XGetPendingTxsResult]

  // update address's all txs status below or equals the given nonce to BLOCK
  def updateInBlock(
      request: XUpdateTxInBlockReq
    ): Future[XUpdateTxInBlockResult]
}

class SettlementTxDalImpl(
  )(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends SettlementTxDal {
  private[this] val logger = Logger(this.getClass)
  val query = TableQuery[SettlementTxTable]
  val timeProvider = new SystemTimeProvider()
  implicit val XStatusCxolumnType = enumColumnType(XSettlementTx.XStatus)

  def saveTx(tx: XSettlementTx): Future[XSaveSettlementTxResult] = {
    db.run(
        (query += tx).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) ⇒
          XSaveSettlementTxResult(ERR_PERSISTENCE_DUPLICATE_INSERT)
        case Failure(ex) ⇒
          logger.error(s"error : ${ex.getMessage}")
          XSaveSettlementTxResult(ERR_PERSISTENCE_INTERNAL)
        case Success(x) ⇒ XSaveSettlementTxResult(ERR_NONE)
      }
  }

  def getPendingTxs(
      request: XGetPendingTxsReq
    ): Future[XGetPendingTxsResult] = {
    implicit val getSupplierResult = GetResult[XSettlementTx](
      r =>
        XSettlementTx(
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextLong,
          XSettlementTx.XStatus.fromValue(r.nextInt),
          r.nextLong,
          r.nextLong
        )
    )
    val sql =
      sql"""
           SELECT tx_hash, `from`, `to`, gas, gas_price, `value`, `data`, MAX(nonce), status, create_at, update_at
            FROM T_SETTLEMENT_TXS
            GROUP BY `from`, nonce
            having `from` = ${request.owner}
             and status = ${XSettlementTx.XStatus.PENDING.value}
             and create_at <= ${request.timeBefore}
        """
        .as[XSettlementTx]
    db.run(sql).map(r => XGetPendingTxsResult(r.toSeq))
  }

  def updateInBlock(
      request: XUpdateTxInBlockReq
    ): Future[XUpdateTxInBlockResult] = {
    val pending: XSettlementTx.XStatus = XSettlementTx.XStatus.PENDING
    val a = (for {
      // update tx in block
      inBlock <- query
        .filter(_.txHash === request.txHash)
        .filter(_.from === request.from)
        .filter(_.nonce === request.nonce)
        .map(_.status)
        .update(XSettlementTx.XStatus.BLOCK)
      _ <- if (inBlock == 1) {
        query
          .filter(_.from === request.from)
          .filter(_.nonce === request.nonce)
          .filter(_.status === pending)
          .map(_.status)
          .update(XSettlementTx.XStatus.FAILED)
      } else {
        throw ErrorException(XErrorCode.ERR_PERSISTENCE_UPDATE_FAILED)
      }
      // update others pending tx to failed
    } yield inBlock).transactionally
    db.run(a).map { r =>
      if (r > 0) XUpdateTxInBlockResult(XErrorCode.ERR_NONE)
      else XUpdateTxInBlockResult(XErrorCode.ERR_INTERNAL_UNKNOWN)
    }
  }
}
