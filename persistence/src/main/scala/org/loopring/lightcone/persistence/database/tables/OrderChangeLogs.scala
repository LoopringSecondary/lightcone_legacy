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

package org.loopring.lightcone.persistence.database.tables

import org.loopring.lightcone.persistence.database.base._
import org.loopring.lightcone.persistence.database.entity.OrderChangeLogEntity
import slick.jdbc.MySQLProfile.api._

class OrderChangeLogs(tag: Tag)
  extends BaseTable[OrderChangeLogEntity](tag, "ORDER_CHANGE_LOGS") {
  def preChangeId = column[Long]("pre_change_id")
  def orderHash = column[String]("order_hash", O.SqlType("VARCHAR(128)"))
  def dealtAmountS = column[String]("dealt_amount_s", O.SqlType("VARCHAR(64)"))
  def dealtAmountB = column[String]("dealt_amount_b", O.SqlType("VARCHAR(64)"))
  def cancelledAmountS = column[String]("cancelled_amount_s", O.SqlType("VARCHAR(64)"))
  def cancelledAmountB = column[String]("cancelled_amount_b", O.SqlType("VARCHAR(64)"))
  def splitAmountS = column[String]("split_amount_s", O.SqlType("VARCHAR(64)"))
  def splitAmountB = column[String]("split_amount_b", O.SqlType("VARCHAR(64)"))
  def status = column[String]("status", O.SqlType("VARCHAR(64)"))
  def updatedBlock = column[Long]("updated_block")
  def * = (
    id,
    createdAt,
    updatedAt,
    preChangeId,
    orderHash,
    dealtAmountS,
    dealtAmountB,
    cancelledAmountS,
    cancelledAmountB,
    status,
    updatedBlock
  ) <> ((OrderChangeLogEntity.apply _).tupled, OrderChangeLogEntity.unapply)

  def idx = index("idx_order_hash", orderHash, unique = true)
}
