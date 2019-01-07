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

import com.google.protobuf.ByteString
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._

class OrderFilledBaseTable(tableNum: Int) {

  class OrderFilledTable(tag: Tag)
      extends BaseTable[OrderFilledData](tag, s"ORDER_FILLED_$tableNum") {
    def id = orderHash
    def orderHash = columnHash("order_hash")
    def txHash = columnHash("tx_hash")
    def owner = columnAddress("owner")
    def blockHash = columnHash("block_hash")
    def blockNumber = column[Long]("block_number")
    def blockTimestamp = column[Long]("block_timestamp")
    def ringHash = columnHash("ring_hash")
    def ringIndex = column[Long]("ring_index")
    def originAmountS = column[ByteString]("origin_amount_s")
    def filledAmountS = column[ByteString]("filled_amount_s")
    def amountFee = column[ByteString]("amount_fee")
    def feeAmountS = column[ByteString]("fee_amount_s")
    def feeAmountB = column[ByteString]("fee_amount_b")
    def feeRecipient = columnAddress("fee_recipient")
    def createdAt = column[Long]("created_at")

    // indexes
    def idx_owner = index("idx_owner", (owner), unique = false)

    def * =
      (
        orderHash,
        txHash,
        owner,
        blockHash,
        blockNumber,
        blockTimestamp,
        ringHash,
        ringIndex,
        originAmountS,
        filledAmountS,
        amountFee,
        feeAmountS,
        feeAmountB,
        feeRecipient,
        createdAt
      ) <> ((OrderFilledData.apply _).tupled, OrderFilledData.unapply)
  }
}
