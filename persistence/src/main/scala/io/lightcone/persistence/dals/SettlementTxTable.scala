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
import io.lightcone.persistence.SettlementTx
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class SettlementTxTable(tag: Tag)
    extends BaseTable[SettlementTx](tag, "T_SETTLEMENT_TXS") {
  implicit val StatusCxolumnType = enumColumnType(SettlementTx.Status)

  def id = txHash
  def txHash = columnHash("tx_hash")
  def from = columnAddress("from")
  def to = columnAddress("to")
  def blockNumber = column[Long]("block_number")
  def gas = column[String]("gas")
  def gasPrice = column[String]("gas_price")
  def value = column[String]("value")
  def data = column[String]("data")
  def nonce = column[Long]("nonce")
  def status = column[SettlementTx.Status]("status")
  def createAt = column[Long]("create_at")
  def updateAt = column[Long]("update_at")

  // indexes
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = true)
  def idx_from = index("idx_from", (from), unique = false)
  def idx_from_nonce = index("idx_from_nonce", (from, nonce), unique = false)
  def idx_status = index("idx_status", (status), unique = false)
  def idx_create_at = index("idx_submit_at", (createAt), unique = false)
  def idx_block = index("idx_block", (blockNumber), unique = false)

  def * =
    (
      txHash,
      from,
      to,
      blockNumber,
      gas,
      gasPrice,
      value,
      data,
      nonce,
      status,
      createAt,
      updateAt
    ) <> ((SettlementTx.apply _).tupled, SettlementTx.unapply)
}
