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
import io.lightcone.persistence.base._
import io.lightcone.persistence._
import io.lightcone.proto._
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._

class BlockDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-block") val dbConfig: DatabaseConfig[JdbcProfile])
    extends BlockDal {

  import ErrorCode._

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
