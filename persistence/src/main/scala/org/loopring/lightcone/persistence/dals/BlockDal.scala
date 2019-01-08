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
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._

trait BlockDal extends BaseDalImpl[BlockTable, BlockData] {

  def saveBlock(block: BlockData): Future[ErrorCode]
  def findByHash(hash: String): Future[Option[BlockData]]
  def findByHeight(height: Long): Future[Option[BlockData]]
  def findMaxHeight(): Future[Option[Long]]

  def findBlocksInHeightRange(
      heightFrom: Long,
      heightTo: Long
    ): Future[Seq[(Long, String)]]
  def count(): Future[Int]
  def obsolete(height: Long): Future[Unit]
}

class BlockDalImpl @Inject()(
    implicit
    @Named("dbconfig-dal-block") val dbConfig: DatabaseConfig[
      JdbcProfile
    ],
    val ec: ExecutionContext)
    extends BlockDal {
  val query = TableQuery[BlockTable]
  def getRowHash(row: BlockData) = row.hash

  def saveBlock(block: BlockData): Future[ErrorCode] =
    for {
      result <- db.run(query.insertOrUpdate(block))
    } yield {
      if (result == 1) {
        ERR_NONE
      } else {
        ERR_PERSISTENCE_INTERNAL
      }
    }

  def findByHash(hash: String): Future[Option[BlockData]] = {
    db.run(
      query
        .filter(_.hash === hash)
        .result
        .headOption
    )
  }

  def findByHeight(height: Long): Future[Option[BlockData]] = {
    db.run(
      query
        .filter(_.height === height)
        .result
        .headOption
    )
  }

  def findMaxHeight(): Future[Option[Long]] = {
    db.run(
      query
        .map(_.height)
        .max
        .result
    )
  }

  def findBlocksInHeightRange(
      heightFrom: Long,
      heightTo: Long
    ): Future[Seq[(Long, String)]] = {
    db.run(
      query
        .filter(_.height >= heightFrom)
        .filter(_.height <= heightTo)
        .sortBy(_.height.asc.nullsFirst)
        .map(c => (c.height, c.hash))
        .result
    )
  }

  def count(): Future[Int] = {
    db.run(query.size.result)
  }

  def obsolete(height: Long): Future[Unit] = {
    db.run(query.filter(_.height >= height).delete).map(_ >= 0)
  }
}
