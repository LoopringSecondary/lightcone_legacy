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
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._

class OrderStateTable(tag: Tag)
  extends BaseTable[XOrderPersState](tag, "T_ORDERS_STATE") {

  implicit val XOrderStatusCxolumnType = enumColumnType(XOrderStatus)
  implicit val XTokenStandardCxolumnType = enumColumnType(XTokenStandard)

  def id = hash
  def hash = columnHash("hash", O.PrimaryKey)
  def tokenS = columnAddress("token_s")
  def tokenB = columnAddress("token_b")
  def tokenFee = columnAddress("token_fee")
  def amountS = columnAmount("amount_s")
  def amountB = columnAmount("amount_b")
  def amountFee = columnAmount("amount_fee")
  def validSince = column[Int]("valid_since")
  def validUntil = column[Int]("valid_until")
  def walletSplitPercentage = column[Double]("wallet_split_percentage")

  // State
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")
  def matchedAt = column[Long]("matched_at")
  def updatedAtBlock = column[Long]("updated_at_block")
  def status = column[XOrderStatus]("status")
  def outstandingAmountS = columnAmount("outstanding_amount_s")
  def outstandingAmountB = columnAmount("outstanding_amount_b")
  def outstandingAmountFee = columnAmount("outstanding_amount_fee")
  def actualAmountS = columnAmount("actual_amount_s")
  def actualAmountB = columnAmount("actual_amount_b")
  def actualAmountFee = columnAmount("actual_amount_fee")

  // indexes
  def idx_hash = index("idx_hash", (hash), unique = true)
  def idx_token_s = index("idx_token_s", (tokenS), unique = false)
  def idx_token_b = index("idx_token_b", (tokenB), unique = false)
  def idx_token_fee = index("idx_token_fee", (tokenFee), unique = false)
  def idx_valid_since = index("idx_valid_since", (validSince), unique = false)
  def idx_valid_until = index("idx_valid_until", (validUntil), unique = false)
  def idx_updated_at = index("idx_updated_at", (updatedAt), unique = false)
  def idx_status = index("idx_status", (status), unique = false)

  def stateProjection = (
    createdAt,
    updatedAt,
    matchedAt,
    updatedAtBlock,
    status,
    actualAmountS,
    actualAmountB,
    actualAmountFee,
    outstandingAmountS,
    outstandingAmountB,
    outstandingAmountFee
  ) <> (
      {
        tuple ⇒
          Option((XOrderPersState.State.apply _).tupled(tuple))
      },
      {
        paramsOpt: Option[XOrderPersState.State] ⇒
          val params = paramsOpt.getOrElse(XOrderPersState.State())
          XOrderPersState.State.unapply(params)
      }
    )

  def * = (
    hash,
    tokenS,
    tokenB,
    tokenFee,
    amountS,
    amountB,
    amountFee,
    validSince,
    validUntil,
    walletSplitPercentage,
    stateProjection
  ) <> ((XOrderPersState.apply _).tupled, XOrderPersState.unapply)
}

