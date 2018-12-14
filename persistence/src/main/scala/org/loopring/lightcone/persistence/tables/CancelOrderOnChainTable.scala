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
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._

class CancelOrderOnChainTable(tag: Tag)
    extends BaseTable[XCancelOrderOnChain](tag, "T_CANCEL_ORDERS_ON_CHAIN") {

  def id = txHash
  def txHash = columnHash("tx_hash")
  def brokerOrOwner = columnAddress("broker_or_owner")
  def orderHash = columnHash("order_hash")
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")
  def blockHeight = column[Long]("block_height")
  def isValid = column[Boolean]("is_valid")

  // indexes
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = true)

  def idx_broker_or_owner =
    index("idx_broker_or_owner", (brokerOrOwner), unique = false)
  def idx_order_hash = index("idx_order_hash", (orderHash), unique = false)

  def idx_block_height =
    index("idx_block_height", (blockHeight), unique = false)
  def idx_is_valid = index("idx_is_valid", (isValid), unique = false)

  def * =
    (
      txHash,
      brokerOrOwner,
      orderHash,
      createdAt,
      updatedAt,
      blockHeight,
      isValid
    ) <> ((XCancelOrderOnChain.apply _).tupled, XCancelOrderOnChain.unapply)
}
