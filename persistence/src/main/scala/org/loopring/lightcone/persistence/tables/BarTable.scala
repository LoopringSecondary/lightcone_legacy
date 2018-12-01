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

package org.loopring.lightcone.persistence.table

import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto.persistence.Bar

class BarTable(tag: Tag)
  extends BaseTable[Bar](tag, "TABLE_BAR") {

  def a = column[String]("A", O.SqlType("VARCHAR(64)"), O.PrimaryKey)
  def b = columnAddress("B")
  def c = columnAmount("C")
  def d = column[Long]("D")

  // indexes
  def idx_c = index("idx_c", (c), unique = false)

  def * = (
    a,
    b,
    c,
    d
  ) <> ((Bar.apply _).tupled, Bar.unapply)
}
