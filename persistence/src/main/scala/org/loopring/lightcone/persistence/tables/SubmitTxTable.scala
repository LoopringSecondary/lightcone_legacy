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

class SubmitTxTable(tag: Tag)
    extends BaseTable[XSubmitTx](tag, "T_SUBMIT_TXS") {
  implicit val XStatusCxolumnType = enumColumnType(XSubmitTx.XStatus)

  def from = columnAddress("from")
  def to = columnAddress("to")
  def gas = column[String]("gas")
  def gasPrice = column[String]("gasPrice")
  def value = column[String]("value")
  def data = column[String]("data")
  def nonce = column[Long]("nonce")
  def status = column[XSubmitTx.XStatus]("status")
  def submitAt = column[Long]("submit_at")

  // indexes
  def idx_from_nonce = index("idx_from_nonce", (from, nonce), unique = true)
  def idx_status = index("idx_status", (status), unique = false)
  def idx_submit_at = index("idx_submit_at", (submitAt), unique = false)

  def * =
    (
      from,
      to,
      gas,
      gasPrice,
      value,
      data,
      nonce,
      status,
      submitAt
    ) <> ((XSubmitTx.apply _).tupled, XSubmitTx.unapply)
}
