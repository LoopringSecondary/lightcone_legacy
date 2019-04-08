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
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.lib.Address
import io.lightcone.ethereum.persistence._
import io.lightcone.persistence._
import io.lightcone.persistence.base._
import org.slf4s.Logging
import slick.basic._
import slick.dbio.Effect
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent._

class ActivityDalImpl @Inject()(
    val shardId: String,
    val dbConfig: DatabaseConfig[JdbcProfile]
  )(
    implicit
    val ec: ExecutionContext)
    extends ActivityDal
    with Logging {

  val query = TableQuery(new ActivityTable(shardId)(_))
  implicit val activityStatusCxolumnType = enumColumnType(TxStatus)
  val STATUS_PENDING: TxStatus = TxStatus.TX_STATUS_PENDING

  def saveActivities(activities: Seq[Activity]) = {
    // set block to a certain value if pending
    val activities_ = activities.map { a =>
      if (a.txStatus == STATUS_PENDING)
        a.copy(block = PENDING_BLOCK_HEIGHT, sequenceId = 0L)
      else a.copy(sequenceId = 0L)
    }
    // save activities
    val op = (for {
      _ <- DBIO.sequence(activities_.map(saveActivityDBIO))
    } yield {}).transactionally
    db.run(op).recover {
      case e: Exception =>
        log.error(
          s"occurs error when save activity:${e.getMessage}, ${e.getCause}"
        )
    }
  }

  def getActivities(
      owner: String,
      token: Option[String],
      sort: SortingType,
      paging: CursorPaging
    ): Future[Seq[Activity]] = {
    var filters = createActivityFilters(owner, token)
    // TODO(yongfeng): 很多dal里都有paging的逻辑，是否可统一？
    filters = sort match {
      case SortingType.DESC =>
        if (paging.cursor > 0) {
          filters
            .filter(_.sequenceId < paging.cursor)
            .sortBy(c => (c.block.desc, c.sequenceId.desc))
        } else { // query latest
          filters.sortBy(c => (c.block.desc, c.sequenceId.desc))
        }
      case _ => // ASC
        if (paging.cursor > 0) {
          filters
            .filter(_.sequenceId > paging.cursor)
            .sortBy(c => (c.block.asc, c.sequenceId.asc))
        } else {
          filters
            .sortBy(c => (c.block.asc, c.sequenceId.asc))
        }
    }
    db.run(
      filters
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

  def deleteActivitiesWithHashes(txHashes: Set[String]): Future[Boolean] =
    db.run(deleteActivitiesWithHashesDBIO(txHashes))
      .map(_ > 0)

  def cleanActivitiesForReorg(req: BlockEvent): Future[Unit] = {
    val op = (for {
      // delete pending and confirmed activities with current block's txHashes
      _ <- deleteActivitiesWithHashesDBIO(req.txs.map(_.txHash).toSet)
      // update all activities which block >= current block height
      _ <- changeActivitiesToPendingDBIO(req.blockNumber)
      // delete the from address's pending activities which nonce <= the tx's nonce
      fromWithMaxNonce = req.txs
        .groupBy(_.from)
        .values
        .map(t => t.maxBy(_.nonce))
      _ <- DBIO.sequence(fromWithMaxNonce.map { r =>
        deletePendingActivitiesWithSmallerNonceDBIO(r.from, r.nonce)
      })
    } yield {}).transactionally
    db.run(op).recover {
      case e: Exception =>
        log.error(
          s"occurs error when cleanActivitiesForReorg:${e.getMessage}, ${e.getCause}"
        )
    }
  }

  private def deleteActivitiesWithHashesDBIO(
      txHashes: Set[String]
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.txHash inSet txHashes)
      .delete

  private def saveActivityDBIO(
      activity: Activity
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query += activity

  private def changeActivitiesToPendingDBIO(
      block: Long
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.block >= block)
      .filterNot(_.block === PENDING_BLOCK_HEIGHT)
      .map(c => (c.block, c.txStatus))
      .update(PENDING_BLOCK_HEIGHT, STATUS_PENDING)

  private def deletePendingByTxHashesDBIO(
      txHash: String
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.txHash === txHash)
      .filter(_.txStatus === STATUS_PENDING)
      .delete

  private def deletePendingActivitiesWithSmallerNonceDBIO(
      fromOfTx: String,
      nonceWithFrom: Long
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.from === fromOfTx)
      .filter(_.block === PENDING_BLOCK_HEIGHT)
      .filter(_.nonce <= nonceWithFrom)
      .delete

  private def createActivityFilters(
      owner: String,
      token: Option[String]
    ) = {
    token match {
      case None => query.filter(_.owner === owner)
      case Some("") =>
        query
          .filter(_.owner === owner)
          .filter(_.token === Address.ZERO.toString()) //以0地址表示以太坊
      case Some(value) =>
        query
          .filter(_.owner === owner)
          .filter(_.token === value)
    }
  }

  def getPendingActivityNonces(
      from: String,
      limit: Int
    ): Future[Seq[Long]] = {
    db.run(
      query
        .filter(_.owner === from)
        .sortBy(_.nonce.desc)
        .distinctOn(_.nonce)
        .take(limit)
        .map(_.nonce)
        .result
    )
  }
}
