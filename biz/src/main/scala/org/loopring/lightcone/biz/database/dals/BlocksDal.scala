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

package org.loopring.lightcone.biz.database.dals

import org.loopring.lightcone.biz.database.OrderDatabase
import org.loopring.lightcone.biz.database.base._
import org.loopring.lightcone.biz.database.entity.BlockEntity
import org.loopring.lightcone.biz.database.tables._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future

trait BlocksDal extends BaseDalImpl[Blocks, BlockEntity] {
  def getBlock(blockHash: String): Future[Option[BlockEntity]]
  def getLatestBlock: Future[Option[BlockEntity]]
  def rollback(start: Long, end: Long): Future[Int]
}

class BlocksDalImpl(val module: OrderDatabase) extends BlocksDal {
  val query = blocksQ

  override def update(row: BlockEntity): Future[Int] = {
    db.run(query.filter(_.id === row.id).update(row))
  }

  override def update(rows: Seq[BlockEntity]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.id === r.id).update(r)): _*))
  }

  def getBlock(blockHash: String): Future[Option[BlockEntity]] = {
    db.run(query.filter(_.blockHash === blockHash).filter(_.fork === false).result.headOption)
  }

  def getLatestBlock: Future[Option[BlockEntity]] = {
    db.run(query.filter(_.fork === false).sortBy(_.id.desc).result.headOption)
  }

  def rollback(start: Long, end: Long): Future[Int] = {
    db.run(query
      .filter(_.blockNumber > start)
      .filter(_.blockNumber <= end)
      .map(c ⇒ (c.fork, c.updatedAt))
      .update(true, module.timeProvider.getTimeSeconds))
  }
}
