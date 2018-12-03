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
import scala.reflect.ClassTag
import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.ethereum._
import com.google.protobuf.ByteString

class TradeTable(tag: Tag)
  extends BaseTable[XTradeData](tag, "T_TRADES") {

  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def height = column[Long]("height")
  def blockHash = columnHash("block_hash")
  def txHash = columnHash("tx_hash")
  def orderHash = columnHash("order_hash")
  def timestamp = column[Long]("timestamp")

  // indexes

  def * = (
    id,
    height,
    blockHash,
    txHash,
    orderHash,
    timestamp
  ) <> ((XTradeData.apply _).tupled, XTradeData.unapply)
}
