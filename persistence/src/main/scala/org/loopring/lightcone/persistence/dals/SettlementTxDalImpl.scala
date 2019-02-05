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

import com.google.inject.Inject
import com.google.inject.name.Named
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib.{ErrorException, SystemTimeProvider}
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.core._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

class SettlementTxDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-settlement-tx") val dbConfig: DatabaseConfig[
      JdbcProfile
    ])
    extends SettlementTxDal {

  import ErrorCode._
  val query = TableQuery[SettlementTxTable]
  val timeProvider = new SystemTimeProvider()
  implicit val StatusCxolumnType = enumColumnType(SettlementTx.Status)

  def saveTx(tx: SettlementTx): Future[PersistSettlementTx.Res] = {
    db.run((query += tx).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        PersistSettlementTx.Res(ERR_PERSISTENCE_DUPLICATE_INSERT)
      case Failure(ex) =>
        logger.error(s"error : ${ex.getMessage}")
        PersistSettlementTx.Res(ERR_PERSISTENCE_INTERNAL)
      case Success(x) => PersistSettlementTx.Res(ERR_NONE)
    }
  }

  def getPendingTxs(request: GetPendingTxs.Req): Future[GetPendingTxs.Res] = {
    implicit val getSupplierResult = GetResult[SettlementTx](
      r =>
        SettlementTx(
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextLong,
          SettlementTx.Status.fromValue(r.nextInt),
          r.nextLong,
          r.nextLong
        )
    )
    val sql =
      sql"""
        SELECT tx_hash, `from`, `to`, gas, gas_price, `value`, `data`, nonce, status, MAX(create_at) as create_at, update_at
        FROM T_SETTLEMENT_TXS
        WHERE `from` = "#${request.owner}"
          and status = #${SettlementTx.Status.PENDING.value}
          and create_at <= #${request.timeBefore}
        GROUP BY `from`, nonce
        """
        .as[SettlementTx]
    db.run(sql).map(r => GetPendingTxs.Res(r.toSeq))
  }

  def updateInBlock(
      request: UpdateTxInBlock.Req
    ): Future[UpdateTxInBlock.Res] = {
    val a = (for {
      // update tx in block
      updateInBlock <- query
        .filter(_.txHash === request.txHash)
        .filter(_.from === request.from)
        .filter(_.nonce === request.nonce)
        .map(_.status)
        .update(SettlementTx.Status.BLOCK)
      updateFaild <- if (updateInBlock == 1) {
        val pending: SettlementTx.Status = SettlementTx.Status.PENDING
        query
          .filter(_.from === request.from)
          .filter(_.nonce === request.nonce)
          .filter(_.status === pending)
          .map(_.status)
          .update(SettlementTx.Status.FAILED)
      } else {
        throw ErrorException(ERR_PERSISTENCE_UPDATE_FAILED)
      }
      // update others pending tx to failed
    } yield updateFaild).transactionally
    db.run(a).map { r =>
      if (r >= 0) UpdateTxInBlock.Res(ERR_NONE)
      else UpdateTxInBlock.Res(ERR_INTERNAL_UNKNOWN)
    }
  }
}
