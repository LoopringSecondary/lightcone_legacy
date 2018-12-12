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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import scala.concurrent._

trait BlockDal
  extends BaseDalImpl[BlockTable, XBlockData] {

  def saveBlock(block: XBlockData): Future[XErrorCode]
  def findByHash(hash: String): Future[Option[XBlockData]]
  def findByHeight(height: Long): Future[Option[XBlockData]]
  def findMaxHeight(): Future[Option[Long]]
  def findBlocksInHeightRange(heightFrom: Long, heightTo: Long): Future[Seq[(Long, String)]]
  def count(): Future[Int]
  def obsolete(height: Long): Future[Unit]
}

class BlockDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends BlockDal {
  val query = TableQuery[BlockTable]
  def getRowHash(row: XBlockData) = row.hash

  def saveBlock(block: XBlockData): Future[XErrorCode] = for {
    result ← db.run(query.insertOrUpdate(block))
  } yield {
    if (result == 1) {
      XErrorCode.ERR_NONE
    } else {
      XErrorCode.PERS_ERR_INTERNAL
    }
  }
  def findByHash(hash: String): Future[Option[XBlockData]] = {
    db.run(query
      .filter(_.hash === hash)
      .filter(_.isValid === 1)
      .result
      .headOption)
  }
  def findByHeight(height: Long): Future[Option[XBlockData]] = {
    db.run(query
      .filter(_.height === height)
      .filter(_.isValid === 1)
      .result
      .headOption)
  }
  def findMaxHeight(): Future[Option[Long]] = {
    db.run(query
      .filter(_.isValid === 1)
      .map(_.height)
      .max
      .result)
  }
  def findBlocksInHeightRange(heightFrom: Long, heightTo: Long): Future[Seq[(Long, String)]] = {
    db.run(query
      .filter(_.height >= heightFrom)
      .filter(_.height <= heightTo)
      .filter(_.isValid === 1)
      .sortBy(_.height.asc.nullsFirst)
      .map(c ⇒ (c.height, c.hash))
      .result)
  }
  def count(): Future[Int] = {
    db.run(query
      .filter(_.isValid === 1)
      .size
      .result)
  }
  def obsolete(height: Long): Future[Unit] = {
    val q = for {
      c ← query if c.height >= height
    } yield c.isValid
    db.run(q.update(0)).map(_ > 0)
  }
}
