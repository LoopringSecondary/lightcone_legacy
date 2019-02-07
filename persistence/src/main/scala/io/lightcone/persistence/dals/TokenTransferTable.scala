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
import scala.reflect.ClassTag
import slick.jdbc.MySQLProfile.api._
import io.lightcone.relayer.data._
import io.lightcone.core._
import com.google.protobuf.ByteString

class TokenTransferTable(tag: Tag)
    extends BaseTable[TokenTransferData](tag, "T_TOKEN_TRANSFERS") {

  // how to generate the id???
  def id = column[String]("id", O.PrimaryKey)
  def height = column[Long]("height")
  def txHash = columnHash("tx_hash")
  def timestamp = column[Long]("timestamp")
  def from = columnAddress("from")
  def to = columnAddress("to")
  def amount = columnAmount("amount")
  def token = columnAddress("token")

  // indexes
  def idx_height = index("idx_height", (height), unique = false)
  def idx_tx_hash = index("idx_tx_hash", (txHash), unique = false)
  def idx_from = index("idx_from", (from), unique = false)
  def idx_to = index("idx_to", (to), unique = false)
  def idx_token = index("idx_token", (token), unique = false)

  def * =
    (id, height, txHash, timestamp, from, to, amount, token) <> ((TokenTransferData.apply _).tupled, TokenTransferData.unapply)
}
