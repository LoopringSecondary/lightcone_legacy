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
import io.lightcone.core.ErrorCode.{ERR_NONE, ERR_PERSISTENCE_INTERNAL}
import io.lightcone.core._
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.lib.Address
import io.lightcone.ethereum.persistence._
import io.lightcone.persistence._
import io.lightcone.persistence.base.enumColumnType
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

  def saveActivity(activity: Activity): Future[ErrorCode] =
    for {
      result <- db.run(query.insertOrUpdate(activity))
    } yield {
      if (result == 1) {
        ERR_NONE
      } else {
        ERR_PERSISTENCE_INTERNAL
      }
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

  def getPendingActivities(from: Set[String]): Future[Seq[Activity]] =
    db.run(query.filter(_.block === 0L).filter(_.from inSet from).result)

  def countActivities(
      owner: String,
      token: Option[String]
    ): Future[Int] = {
    val filters = createActivityFilters(owner, token)
    db.run(filters.size.result)
  }

  def deleteByTxHashes(txHashes: Set[String]): Future[Boolean] =
    db.run(
        query
          .filter(_.txHash inSet txHashes)
          .delete
      )
      .map(_ > 0)

  def cleanUpForBlockReorganization(req: BlockEvent): Future[Unit] = {
    val a = (for {
      _ <- updateBlockActivitiesToPendingDBIO(req.blockNumber)
      txsWithMaxNonce = req.txs
        .groupBy(_.from)
        .values
        .map(t => t.maxBy(_.nonce))
      _ <- DBIO.sequence(txsWithMaxNonce.map { r =>
        deletePendingActivitiesWithFromNonceDBIO(r.from, r.nonce)
      })
    } yield {}).transactionally
    db.run(a)
  }

  private def updateBlockActivitiesToPendingDBIO(
      block: Long
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.block >= block)
      .map(c => (c.block, c.sequenceId, c.txStatus))
      //TODO (yongfeng) create pending sequenceId with from and nonce
      .update(0L, 0L, TxStatus.TX_STATUS_PENDING)

  private def deletePendingActivitiesWithFromNonceDBIO(
      fromOfTx: String,
      nonceWithFrom: Int
    ): FixedSqlAction[Int, NoStream, Effect.Write] =
    query
      .filter(_.from === fromOfTx)
      .filter(_.block === 0L)
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
