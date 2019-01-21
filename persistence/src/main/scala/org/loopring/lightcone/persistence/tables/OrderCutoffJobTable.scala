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

package org.loopring.lightcone.persistence.tables

import org.loopring.lightcone.persistence.base.BaseTable
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._

class OrderCutoffJobTable(tag: Tag)
    extends BaseTable[OrderCutoffJob](tag, "T_ORDER_CUTOFF_JOB") {

  def id = ""
  def broker = columnAddress("broker")
  def owner = columnAddress("owner")
  def tradingPair = columnAddress("trading_pair")
  def cutoff = column[Long]("cutoff")
  def createdAt = column[Long]("created_at")

  // indexes
  def pk = primaryKey("pk_cutoff", (broker, owner, tradingPair, cutoff))

  def * =
    (broker, owner, tradingPair, cutoff, createdAt) <> ((OrderCutoffJob.apply _).tupled, OrderCutoffJob.unapply)

}
