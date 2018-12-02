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
import org.loopring.lightcone.proto.persistence.Bar
import org.loopring.lightcone.proto.core._
import slick.dbio.Effect
import slick.jdbc.MySQLProfile.api._
import slick.sql.FixedSqlAction

import scala.concurrent.Future

trait BarsDal extends BaseDalImpl[BarTable, Bar] with TableQuery[BarTable] {

}

class BarsDalImpl(val module: BaseDatabaseModule) extends BarsDal with TableQuery[BarTable] {
  val query = TableQuery[BarTable]

  def update(row: Bar): Future[Int] = {
    db.run(query.filter(_.id === row.id).update(row))
  }

  def update(rows: Seq[Bar]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.id === r.id).update(r)): _*))
  }
}
