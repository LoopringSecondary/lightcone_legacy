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
import slick.jdbc.MySQLProfile.api._
import io.lightcone.ethereum.persistence._

class FillTable(tag: Tag) extends BaseTable[Fill](tag, "T_TRADES") {

  def id = ""
  def owner = columnAddress("owner")
  def orderHash = columnHash("order_hash")
  def ringHash = columnHash("ring_hash")
  def ringIndex = column[Long]("ring_index")
  def fillIndex = column[Int]("fill_index")
  def txHash = columnHash("tx_hash")
  def amountS = columnAmount("amount_s")
  def amountB = columnAmount("amount_b")
  def tokenS = columnAddress("token_s")
  def tokenB = columnAddress("token_b")
  def marketId = column[Long]("market_id")
  def split = columnAmount("split")

  // fees
  def tokenFee = columnAddress("token_fee")
  def amountFee = columnAmount("amount_fee")
  def feeAmountS = columnAmount("fee_amount_s")
  def feeAmountB = columnAmount("fee_amount_b")
  def feeRecipient = columnAddress("fee_recipient")
  def waiveFeePercentage = column[Int]("waive_fee_percentage")
  def walletSplitPercentage = column[Int]("wallet_split_percentage")

  def wallet = columnAddress("wallet")
  def miner = columnAddress("miner")
  def blockHeight = column[Long]("block_height")
  def blockTimestamp = column[Long]("block_timestamp")

  // indexes
  def pk = primaryKey("pk_txhash_fillindex", (txHash, fillIndex))
  def idx_ring_hash = index("idx_ring_hash", (ringHash), unique = false)
  def idx_ring_index = index("idx_ring_index", (ringIndex), unique = false)
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = false)
  def idx_owner = index("idx_owner", (owner), unique = false)
  def idx_order_hash = index("idx_order_hash", (orderHash), unique = false)
  def idx_token_s = index("idx_token_s", (tokenS), unique = false)
  def idx_token_b = index("idx_token_b", (tokenB), unique = false)
  def idx_market_id = index("idx_market_id", (marketId), unique = false)
  def idx_wallet = index("idx_wallet", (wallet), unique = false)
  def idx_miner = index("idx_miner", (miner), unique = false)

  def idx_block_height =
    index("idx_block_height", (blockHeight), unique = false)

  def feeParamsProjection =
    (
      tokenFee,
      amountFee,
      feeAmountS,
      feeAmountB,
      feeRecipient,
      waiveFeePercentage,
      walletSplitPercentage
    ) <> ({ tuple =>
      Option((Fill.Fee.apply _).tupled(tuple))
    }, { paramsOpt: Option[Fill.Fee] =>
      val params = paramsOpt.getOrElse(Fill.Fee())
      Fill.Fee.unapply(params)
    })

  def * =
    (
      owner,
      orderHash,
      ringHash,
      ringIndex,
      fillIndex,
      txHash,
      amountS,
      amountB,
      tokenS,
      tokenB,
      marketId,
      split,
      feeParamsProjection,
      wallet,
      miner,
      blockHeight,
      blockTimestamp
    ) <> ((Fill.apply _).tupled, Fill.unapply)
}
