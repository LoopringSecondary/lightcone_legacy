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

package org.loopring.lightcone.auxiliary.database.tables

import org.loopring.lightcone.auxiliary.database.base._
import org.loopring.lightcone.auxiliary.database.entity.OrderEntity
import slick.collection.heterogeneous.HNil
import slick.jdbc.MySQLProfile.api._

class Orders(tag: Tag) extends BaseTable[OrderEntity](tag, "ORDERS") {
  def version = column[String]("version", O.SqlType("VARCHAR(64)"))

  def owner = column[String]("owner", O.SqlType("VARCHAR(64)"))

  def tokenS = column[String]("token_s", O.SqlType("VARCHAR(64)"))

  def tokenB = column[String]("token_b", O.SqlType("VARCHAR(64)"))

  def amountS = column[String]("amount_s", O.SqlType("VARCHAR(64)"))

  def amountB = column[String]("amount_b", O.SqlType("VARCHAR(64)"))

  def validSince = column[Long]("valid_since")

  def tokenSpendableS = column[String]("token_spendable_s", O.SqlType("VARCHAR(64)"))

  def tokenSpendableFee = column[String]("token_spendable_fee", O.SqlType("VARCHAR(64)"))

  def dualAuthAddress = column[String]("dual_auth_address", O.SqlType("VARCHAR(64)"))

  def broker = column[String]("broker", O.SqlType("VARCHAR(64)"))

  def brokerSpendableS = column[String]("broker_spendable_s", O.SqlType("VARCHAR(64)"))

  def brokerSpendableFee = column[String]("broker_spendable_fee", O.SqlType("VARCHAR(64)"))

  def orderInterceptor = column[String]("order_interceptor", O.SqlType("VARCHAR(64)"))

  def wallet = column[String]("wallet", O.SqlType("VARCHAR(64)"))

  def validUntil = column[Long]("valid_until")

  def sig = column[String]("sig", O.SqlType("VARCHAR(256)"))

  def dualAuthSig = column[String]("dual_auth_sig", O.SqlType("VARCHAR(256)"))

  def allOrNone = column[Boolean]("all_or_none")

  def feeToken = column[String]("fee_token", O.SqlType("VARCHAR(64)"))

  def feeAmount = column[String]("fee_amount", O.SqlType("VARCHAR(64)"))

  def feePercentage = column[Int]("fee_percentage")

  def waiveFeePercentage = column[Int]("waive_fee_percentage")

  def tokenSFeePercentage = column[Int]("token_s_fee_percentage")

  def tokenBFeePercentage = column[Int]("token_b_fee_percentage")

  def tokenRecipient = column[String]("token_recipient", O.SqlType("VARCHAR(64)"))

  def walletSplitPercentage = column[Int]("wallet_split_percentage")

  def dualPrivateKey = column[String]("dual_private_key", O.SqlType("VARCHAR(128)"))
  def hash = column[String]("order_hash", O.SqlType("VARCHAR(128)"))

  def powNonce = column[Long]("pow_nonce")

  def updatedBlock = column[Long]("updated_block")

  def dealtAmountS = column[String]("dealt_amount_s", O.SqlType("VARCHAR(64)"))

  def dealtAmountB = column[String]("dealt_amount_b", O.SqlType("VARCHAR(64)"))

  def cancelledAmountS = column[String]("cancelled_amount_s", O.SqlType("VARCHAR(64)"))

  def cancelledAmountB = column[String]("cancelled_amount_b", O.SqlType("VARCHAR(64)"))

  def status = column[String]("status", O.SqlType("VARCHAR(64)"))

  //  def minerBlockMark = column[Long]("miner_block_mark")
  def broadcastTime = column[Int]("broadcast_time")

  def market = column[String]("market", O.SqlType("VARCHAR(32)"))

  def side = column[String]("side", O.SqlType("VARCHAR(32)"))

  def price = column[Double]("price", O.SqlType("DECIMAL(28,16)"))

  def orderType = column[String]("order_type", O.SqlType("VARCHAR(32)"))

  def idx = index("idx_order_hash", hash, unique = true)

  def * = (id ::
    updatedAt ::
    createdAt ::
    version ::
    owner ::
    tokenS ::
    tokenB ::
    amountS ::
    amountB ::
    validSince ::
    tokenSpendableS ::
    tokenSpendableFee ::
    dualAuthAddress ::
    broker ::
    brokerSpendableS ::
    brokerSpendableFee ::
    orderInterceptor ::
    wallet ::
    validUntil ::
    sig ::
    dualAuthSig ::
    allOrNone ::
    feeToken ::
    feeAmount ::
    feePercentage ::
    waiveFeePercentage ::
    tokenSFeePercentage ::
    tokenBFeePercentage ::
    tokenRecipient ::
    walletSplitPercentage ::
    dualPrivateKey ::
    hash ::
    powNonce ::
    updatedBlock ::
    dealtAmountS ::
    dealtAmountB ::
    cancelledAmountS ::
    cancelledAmountB ::
    status ::
    broadcastTime ::
    market ::
    side ::
    price ::
    orderType :: HNil).mapTo[OrderEntity]
}
