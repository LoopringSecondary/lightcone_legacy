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

package org.loopring.lightcone.persistence.database.dals

import org.loopring.lightcone.persistence.database.OrderDatabase
import org.loopring.lightcone.persistence.database.base._
import org.loopring.lightcone.persistence.database.entity.OrderChangeLogEntity
import org.loopring.lightcone.persistence.database.tables._
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Future

trait OrderChangeLogsDal
  extends BaseDalImpl[OrderChangeLogs, OrderChangeLogEntity] {
  def addChangeLog(change: OrderChangeLogEntity): Future[Int]
  def getLogsByHash(orderHash: String): Future[Seq[OrderChangeLogEntity]]
}

class OrderChangeLogsDalImpl(val module: OrderDatabase)
  extends OrderChangeLogsDal {
  val query = orderChangeLogsQ

  override def update(row: OrderChangeLogEntity): Future[Int] = {
    db.run(query.filter(_.id === row.id).update(row))
  }

  override def update(rows: Seq[OrderChangeLogEntity]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.id === r.id).update(r)): _*))
  }

  def addChangeLog(change: OrderChangeLogEntity): Future[Int] = module.db.run(query += change)

  def getLogsByHash(orderHash: String): Future[Seq[OrderChangeLogEntity]] = {
    db.run(query.filter(_.orderHash === orderHash).result)
  }
}
