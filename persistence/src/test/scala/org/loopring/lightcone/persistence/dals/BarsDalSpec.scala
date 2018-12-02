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

import scala.concurrent.duration._
import scala.concurrent.Await
import slick.jdbc.meta._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import org.loopring.lightcone.proto.persistence.Bar
import com.google.protobuf.ByteString
import scala.concurrent._
import org.loopring.lightcone.persistence.base._

private[dals] class BarTable(tag: Tag)
  extends BaseTable[Bar](tag, "T_BAR") {

  def id = column[String]("id", O.SqlType("VARCHAR(64)"), O.PrimaryKey)
  def a = column[String]("A")
  def b = columnAddress("B")
  def c = columnAmount("C")
  def d = column[Long]("D")

  // indexes
  def idx_c = index("idx_c", (c), unique = false)

  def * = (
    id,
    a,
    b,
    c,
    d
  ) <> ((Bar.apply _).tupled, Bar.unapply)
}

private[dals] trait BarsDal extends BaseDalImpl[BarTable, Bar] {

}

private[dals] class BarsDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends BarsDal {
  val query = TableQuery[BarTable]

  def update(row: Bar): Future[Int] = {
    db.run(query.filter(_.id === row.id).update(row))
  }

  def update(rows: Seq[Bar]): Future[Unit] = {
    db.run(DBIO.seq(rows.map(r ⇒ query.filter(_.id === r.id).update(r)): _*))
  }
}

class BarsDalSpec extends DalSpec[BarsDal] {
  val dal = new BarsDalImpl()

  "A" must "b" in {
    println("========>>" + dal.tableName)
    val bar = Bar("a", "b", "c", ByteString.copyFrom("d".getBytes), 12L)

    // Await.result(dal.insert(bar), 1 second)
  }
}
