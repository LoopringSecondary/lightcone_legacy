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
import io.lightcone.lib._
import io.lightcone.persistence.base._
import io.lightcone.core._
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.persistence.SettlementTx
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
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

  def saveTx(tx: SettlementTx): Future[ErrorCode] = {
    db.run((query += tx).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) =>
        ERR_PERSISTENCE_DUPLICATE_INSERT
      case Failure(ex) =>
        logger.error(s"error : ${ex.getMessage}")
        ERR_PERSISTENCE_INTERNAL
      case Success(x) => ERR_NONE
    }
  }

  def getPendingTxs(
      owner: String,
      timeBefore: Long
    ): Future[Seq[SettlementTx]] = {
    implicit val getSupplierResult = GetResult[SettlementTx](
      r =>
        SettlementTx(
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextLong,
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
        SELECT tx_hash, `from`, `to`, block_number, gas, gas_price, `value`, `data`, nonce, status, MAX(create_at) as create_at, update_at
        FROM T_SETTLEMENT_TXS
        WHERE `from` = "#${owner}"
          and status = #${SettlementTx.Status.PENDING.value}
          and create_at <= #${timeBefore}
        GROUP BY `from`, nonce
        """
        .as[SettlementTx]
    db.run(sql).map(r => r.toSeq)
  }

  def updateInBlock(
      txHash: String,
      from: String,
      nonce: Long
    ): Future[ErrorCode] = {
    val a = (for {
      // update tx in block
      updateInBlock <- query
        .filter(_.txHash === txHash)
        .filter(_.from === from)
        .filter(_.nonce === nonce)
        .map(_.status)
        .update(SettlementTx.Status.BLOCK)
      updateFaild <- if (updateInBlock >= 1) {
        val pending: SettlementTx.Status = SettlementTx.Status.PENDING
        query
          .filter(_.from === from)
          .filter(_.nonce === nonce)
          .filter(_.status === pending)
          .map(_.status)
          .update(SettlementTx.Status.FAILED)
      } else {
        throw ErrorException(ERR_PERSISTENCE_UPDATE_FAILED)
      }
      // update others pending tx to failed
    } yield updateFaild).transactionally
    db.run(a).map { r =>
      if (r >= 0) ERR_NONE
      else ERR_INTERNAL_UNKNOWN
    }
  }

  def cleanTxsForReorg(event: BlockEvent): Future[Int] = {
    db.run(
      query
        .filter(_.blockNumber >= event.blockNumber)
        .delete
    )
  }
}
