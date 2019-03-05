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
    extends ActivityDal {

  val query = TableQuery(new ActivityTable(shardId)(_))
  implicit val activityStatusCxolumnType = enumColumnType(TxStatus)
  val pendingTxStatus: TxStatus = TxStatus.TX_STATUS_PENDING

  def saveActivities(activities: Seq[Activity]) = {
    // set block to a certain value if pending
    val activities_ = activities.map { a =>
      if (a.txStatus == pendingTxStatus)
        a.copy(block = ACTIVITY_PENDING_BLOCK_HEIGHT)
      else a
    }
    val a = (for {
      // delete pending activities with current block's txHash
      _ <- deletePendingByTxHashesDBIO(activities_.head.txHash)
      // save activities
      _ <- DBIO.sequence(activities_.map(saveActivityDBIO))
    } yield {}).transactionally
    db.run(a)
  }

  def getActivities(
      owner: String,
      token: Option[String],
      paging: CursorPaging
    ): Future[Seq[Activity]] = {
    val filters = createActivityFilters(owner, token)
    db.run(
      filters
        .filter(_.sequenceId > paging.cursor)
        .sortBy(c => c.sequenceId.desc)
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

  def deleteByTxHashesDBIO(
      txHashes: Set[String]
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.txHash inSet txHashes)
      .delete

  def deleteByTxHashes(txHashes: Set[String]): Future[Boolean] =
    db.run(deleteByTxHashesDBIO(txHashes))
      .map(_ > 0)

  def cleanUpForBlockReorganization(req: BlockEvent): Future[Unit] = {
    val a = (for {
      // delete pending and blocked activities with current block's txHashes
      _ <- deleteByTxHashesDBIO(req.txs.map(_.txHash).toSet)
      // update all activities which block above current block height
      _ <- updateBlockActivitiesToPendingDBIO(req.blockNumber)
      // delete the from address's in txs which nonce blow or equals to the tx's nonce
      txsWithMaxNonce = req.txs
        .groupBy(_.from)
        .values
        .map(t => t.maxBy(_.nonce))
      _ <- DBIO.sequence(txsWithMaxNonce.map { r =>
        deletePendingActivitiesWhenFromNonceBlowGivenValueDBIO(r.from, r.nonce)
      })
    } yield {}).transactionally
    db.run(a)
  }

  private def saveActivityDBIO(
      activity: Activity
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query += activity

  private def updateBlockActivitiesToPendingDBIO(
      block: Long
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.block >= block)
      .filterNot(_.block === ACTIVITY_PENDING_BLOCK_HEIGHT)
      .map(c => (c.block, c.txStatus))
      .update(ACTIVITY_PENDING_BLOCK_HEIGHT, pendingTxStatus)

  private def deletePendingByTxHashesDBIO(
      txHash: String
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.txHash === txHash)
      .filter(_.txStatus === pendingTxStatus)
      .delete

  private def deletePendingActivitiesWhenFromNonceBlowGivenValueDBIO(
      fromOfTx: String,
      nonceWithFrom: Int
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.from === fromOfTx)
      .filter(_.block === ACTIVITY_PENDING_BLOCK_HEIGHT)
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

}
