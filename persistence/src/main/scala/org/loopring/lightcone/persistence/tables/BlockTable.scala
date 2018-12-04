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
import scala.reflect.ClassTag
import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.ethereum._
import com.google.protobuf.ByteString

class BlockTable(tag: Tag)
  extends BaseTable[XBlockData](tag, "T_BLOCKS") {

  def id = hash
  def hash = columnHash("hash", O.PrimaryKey)
  def height = column[Long]("height")
  def timestamp = column[Long]("timestamp")
  def numTx = column[Int]("num_tx")
  def parentHash = columnHash("parent_hash")
  def sha3Uncles = columnHash("sha3_uncles")
  def minedBy = columnAddress("mined_by")
  def difficulty = columnAmount("difficulty")
  def totalDifficulty = columnAmount("total_difficulty")
  def size = column[Long]("size")
  def gasUsed = columnAmount("gas_used")
  def gasLimit = columnAmount("gas_limit")
  def avgGasPrice = column[Long]("avg_gas_price")
  def nonce = column[Long]("nonce")
  def blockReward = columnAmount("block_reward")
  def uncleReward = columnAmount("uncle_reward")
  def extraData = column[ByteString]("extra_data")

  // indexes
  def idx_parent_hash = index("idx_parent_hash", (parentHash), unique = false)
  def idx_height = index("idx_height", (height), unique = false)
  // def idx_mined_by = index("idx_mined_by", (minedBy), unique = false)

  def * = (
    hash,
    height,
    timestamp,
    numTx,
    parentHash,
    sha3Uncles,
    minedBy,
    difficulty,
    totalDifficulty,
    size,
    gasUsed,
    gasLimit,
    avgGasPrice,
    nonce,
    blockReward,
    uncleReward,
    extraData
  ) <> ((XBlockData.apply _).tupled, XBlockData.unapply)
}
