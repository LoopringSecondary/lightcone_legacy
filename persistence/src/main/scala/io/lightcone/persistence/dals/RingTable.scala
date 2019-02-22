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

import io.lightcone.persistence.base._
import io.lightcone.persistence._
import slick.jdbc.MySQLProfile.api._

class RingTable(tag: Tag) extends BaseTable[Ring](tag, "T_RINGS") {

  implicit val feesColumnType = ringFeesOptColumnType()

  def id = ringIndex.toString()
  def ringHash = columnHash("ring_hash")
  def ringIndex = column[Long]("ring_index", O.PrimaryKey)
  def fillsAmount = column[Int]("fills_amount")
  def miner = columnAddress("miner")
  def txHash = columnHash("tx_hash")
  def fees = column[Option[Ring.Fees]]("fees")
  def blockHeight = column[Long]("block_height")
  def blockTimestamp = column[Long]("block_timestamp")

  // indexes
  def idx_ring_hash = index("idx_ring_hash", (ringHash), unique = false)
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = true)
  def idx_miner = index("idx_miner", (miner), unique = false)

  def idx_block_height =
    index("idx_block_height", (blockHeight), unique = false)

  def * =
    (
      ringHash,
      ringIndex,
      fillsAmount,
      miner,
      txHash,
      fees,
      blockHeight,
      blockTimestamp
    ) <> ((Ring.apply _).tupled, Ring.unapply)
}
