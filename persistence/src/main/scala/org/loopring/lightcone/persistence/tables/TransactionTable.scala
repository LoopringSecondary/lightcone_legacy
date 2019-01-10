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
import org.loopring.lightcone.proto._
import com.google.protobuf.ByteString

class TransactionTable(tag: Tag)
    extends BaseTable[TransactionData](tag, "T_TRANSACTIONS") {

  def id = hash
  def hash = columnHash("hash", O.PrimaryKey)
  def height = column[Long]("height")
  def blockHash = columnHash("block_hash")
  def timestamp = column[Long]("timestamp")
  def from = columnAddress("from")
  def to = columnAddress("to")
  def amount = columnAmount("amount")
  def txReceiptStatus = column[Int]("tx_receipt_status")
  def gasUsed = columnAmount("gas_used")
  def gasLimit = columnAmount("gas_limit")
  def gasPrice = column[Long]("gas_price")
  def txFee = columnAmount("tx_fee")
  def nonce = column[Long]("nonce")
  def inputData = column[ByteString]("input_data")

  // indexes
  def idx_height = index("idx_height", (height), unique = false)
  def idx_hash = index("idx_hash", (hash), unique = true)
  def idx_block_hash = index("idx_block_hash", (blockHash), unique = false)
  def idx_from = index("idx_from", (from), unique = false)
  def idx_to = index("idx_to", (to), unique = false)

  def * =
    (
      hash,
      height,
      blockHash,
      timestamp,
      from,
      to,
      amount,
      txReceiptStatus,
      gasUsed,
      gasLimit,
      gasPrice,
      txFee,
      nonce,
      inputData
    ) <> ((TransactionData.apply _).tupled, TransactionData.unapply)
}
