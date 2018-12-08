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
import org.loopring.lightcone.proto.core._

class OrderTable(tag: Tag)
  extends BaseTable[XRawOrder](tag, "T_ORDERS") {

  implicit val XOrderStatusCxolumnType = enumColumnType(XOrderStatus)
  implicit val XTokenStandardCxolumnType = enumColumnType(XTokenStandard)

  def id = hash
  def hash = columnHash("hash")
  def version = column[Int]("version")
  def owner = columnAddress("owner")
  def tokenS = columnAddress("token_s")
  def tokenB = columnAddress("token_b")
  def amountS = columnAmount("amount_s")
  def amountB = columnAmount("amount_b")
  def validSince = column[Int]("valid_since")

  // Params
  def dualAuthAddr = columnAddress("dual_auth_addr")
  def broker = columnAddress("broker")
  def orderInterceptor = columnAddress("order_interceptor")
  def wallet = columnAddress("wallet")
  def validUntil = column[Int]("valid_until")
  def sig = column[String]("sig")
  def dualAuthPrivKey = column[String]("dual_auth_priv_key")
  def allOrNone = column[Boolean]("all_or_none")
  def tokenStandardS = column[XTokenStandard]("token_standard_s")
  def tokenStandardB = column[XTokenStandard]("token_standard_b")
  def tokenStandardFee = column[XTokenStandard]("token_standard_fee")

  // FeeParams
  def tokenFee = columnAddress("token_fee")
  def amountFee = columnAmount("amount_fee")
  def waiveFeePercentage = column[Int]("waive_fee_percentage")
  def tokenSFeePercentage = column[Int]("token_s_fee_percentage")
  def tokenBFeePercentage = column[Int]("token_b_fee_percentage")
  def tokenRecipient = columnAddress("token_recipient") // ???
  def walletSplitPercentage = column[Int]("wallet_split_percentage")

  // ERC1400
  def trancheS = column[String]("tranche_s")
  def trancheB = column[String]("tranche_b")
  def trancheDataS = column[String]("transfer_data_s")

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

  def sequenceId = column[Long]("sequence_id", O.PrimaryKey, O.AutoInc)

  // indexes
  def idx_hash = index("idx_hash", (hash), unique = true)
  def idx_updated_at = index("idx_updated_at", (updatedAt), unique = false)
  def idx_status = index("idx_status", (status), unique = false)
  def idx_token_s = index("idx_token_s", (tokenS), unique = false)
  def idx_token_b = index("idx_token_b", (tokenB), unique = false)
  def idx_token_fee = index("idx_token_fee", (tokenFee), unique = false)
  def idx_valid_since = index("idx_valid_since", (validSince), unique = false)
  def idx_valid_until = index("idx_valid_until", (validUntil), unique = false)
  def idx_owner = index("idx_owner", (owner), unique = false)
  def idx_wallet = index("idx_wallet", (wallet), unique = false)
  // def idx_sequence = index("idx_sequence", (sequenceId), unique = true)

  def paramsProjection = (
    dualAuthAddr,
    broker,
    orderInterceptor,
    wallet,
    validUntil,
    sig,
    dualAuthPrivKey,
    allOrNone,
    tokenStandardS,
    tokenStandardB,
    tokenStandardFee
  ) <> (
      {
        tuple ⇒
          Option((XRawOrder.Params.apply _).tupled(tuple))
      },
      {
        paramsOpt: Option[XRawOrder.Params] ⇒
          val params = paramsOpt.getOrElse(XRawOrder.Params())
          XRawOrder.Params.unapply(params)
      }
    )

  def feeParamsProjection = (
    tokenFee,
    amountFee,
    waiveFeePercentage,
    tokenSFeePercentage,
    tokenBFeePercentage,
    tokenRecipient,
    walletSplitPercentage
  ) <> (
      {
        tuple ⇒
          Option((XRawOrder.FeeParams.apply _).tupled(tuple))
      },
      {
        paramsOpt: Option[XRawOrder.FeeParams] ⇒
          val params = paramsOpt.getOrElse(XRawOrder.FeeParams())
          XRawOrder.FeeParams.unapply(params)
      }
    )

  def erc1400ParamsProjection = (
    trancheS,
    trancheB,
    trancheDataS
  ) <> (
      {
        tuple ⇒
          Option((XRawOrder.ERC1400Params.apply _).tupled(tuple))
      },
      {
        paramsOpt: Option[XRawOrder.ERC1400Params] ⇒
          val params = paramsOpt.getOrElse(XRawOrder.ERC1400Params())
          XRawOrder.ERC1400Params.unapply(params)
      }
    )

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
          Option((XRawOrder.State.apply _).tupled(tuple))
      },
      {
        paramsOpt: Option[XRawOrder.State] ⇒
          val params = paramsOpt.getOrElse(XRawOrder.State())
          XRawOrder.State.unapply(params)
      }
    )

  def * = (
    hash,
    version,
    owner,
    tokenS,
    tokenB,
    amountS,
    amountB,
    validSince,
    paramsProjection,
    feeParamsProjection,
    erc1400ParamsProjection,
    stateProjection,
    sequenceId
  ) <> ((XRawOrder.apply _).tupled, XRawOrder.unapply)
}

