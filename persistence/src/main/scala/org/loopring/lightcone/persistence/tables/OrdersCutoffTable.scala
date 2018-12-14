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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._

class OrdersCutoffTable(tag: Tag)
    extends BaseTable[XOrdersCutoffEvent](tag, "T_ORDERS_CUTOFF") {

  def id = txHash
  def txHash = columnHash("tx_hash")
  def broker = columnAddress("broker")
  def owner = columnAddress("owner")
  def tradingPair = column[String]("trading_pair", O.Length(100))
  def cutoff = column[Long]("cutoff")
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")
  def blockHeight = column[Long]("block_height")

  // indexes
  def idx_hash = index("idx_hash", (txHash), unique = true)
  def idx_broker = index("idx_broker", (broker), unique = false)
  def idx_owner = index("idx_owner", (owner), unique = false)

  def idx_trading_pair =
    index("idx_trading_pair", (tradingPair), unique = false)
  def idx_created_at = index("idx_created_at", (createdAt), unique = false)

  def idx_block_height =
    index("idx_block_height", (blockHeight), unique = false)

  def * =
    (
      txHash,
      broker,
      owner,
      tradingPair,
      cutoff,
      createdAt,
      updatedAt,
      blockHeight
    ) <> ((XOrdersCutoffEvent.apply _).tupled, XOrdersCutoffEvent.unapply)
}
