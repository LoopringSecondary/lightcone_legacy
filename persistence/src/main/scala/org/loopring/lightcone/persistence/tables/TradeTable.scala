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
import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto._

class TradeTable(tag: Tag) extends BaseTable[Trade](tag, "T_TRADES") {

  def id = txHash
  def delegateAddress = columnAddress("delegate_address")
  def owner = columnAddress("owner")
  def orderHash = columnHash("order_hash")
  def ringHash = columnHash("ring_hash")
  def ringIndex = column[Long]("ring_index")
  def txHash = columnHash("tx_hash")
  def amountS = columnAmount("amount_s")
  def amountB = columnAmount("amount_b")
  def tokenS = columnAddress("token_s")
  def tokenB = columnAddress("token_b")
  def split = columnAmount("split")

  // fees
  def tokenFee = columnAddress("token_fee")
  def amountFee = columnAmount("amount_fee")
  def tokenAmountS = columnAddress("token_amount_s")
  def feeAmountS = columnAmount("fee_amount_s")
  def tokenAmountB = columnAddress("token_amount_b")
  def feeAmountB = columnAmount("fee_amount_b")
  def feeRecipient = columnAddress("fee_recipient")

  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")
  def blockHeight = column[Long]("block_height")
  def blockTimestamp = column[Long]("block_timestamp")
  def sequenceId = column[Long]("sequence_id", O.PrimaryKey, O.AutoInc)
  def marketKey = columnAddress("market_key")

  // indexes
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = true)
  def idx_owner = index("idx_owner", (owner), unique = false)
  def idx_order_hash = index("idx_order_hash", (orderHash), unique = false)
  def idx_ring_hash = index("idx_ring_hash", (ringHash), unique = false)
  def idx_ring_index = index("idx_ring_index", (ringIndex), unique = false)
  def idx_token_s = index("idx_token_s", (tokenS), unique = false)
  def idx_token_b = index("idx_token_b", (tokenB), unique = false)
  def idx_market_key = index("idx_market_key", (marketKey), unique = false)

  def idx_block_height =
    index("idx_block_height", (blockHeight), unique = false)

  def feeParamsProjection =
    (
      tokenFee,
      amountFee,
      tokenAmountS,
      feeAmountS,
      tokenAmountB,
      feeAmountB,
      feeRecipient
    ) <> ({ tuple =>
      Option((Trade.Fees.apply _).tupled(tuple))
    }, { paramsOpt: Option[Trade.Fees] =>
      val params = paramsOpt.getOrElse(Trade.Fees())
      Trade.Fees.unapply(params)
    })

  def * =
    (
      owner,
      delegateAddress,
      orderHash,
      ringHash,
      ringIndex,
      txHash,
      amountS,
      amountB,
      tokenS,
      tokenB,
      marketKey,
      split,
      feeParamsProjection,
      createdAt,
      updatedAt,
      blockHeight,
      blockTimestamp,
      sequenceId
    ) <> ((Trade.apply _).tupled, Trade.unapply)
}
