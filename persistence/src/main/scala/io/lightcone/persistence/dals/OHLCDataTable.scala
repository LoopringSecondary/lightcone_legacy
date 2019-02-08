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
import io.lightcone.relayer.data._

object OHLCDataTable {
  val tableName = "T_OHLC_DATA"
}

class OHLCDataTable(tag: Tag)
    extends BaseTable[OHLCRawData](tag, OHLCDataTable.tableName) {

  def id = txHash
  def ringIndex = column[Long]("ring_index")
  def txHash = columnHash("tx_hash")
  def marketHash = column[String]("market_hash")
  def time = column[Long]("time")
  def baseAmount = column[Double]("base_amount", O.SqlType("DOUBLE PRECISION"))

  def quoteAmount =
    column[Double]("quote_amount", O.SqlType("DOUBLE PRECISION"))
  def price = column[Double]("price", O.SqlType("DOUBLE PRECISION"))

  def * =
    (ringIndex, txHash, marketHash, time, baseAmount, quoteAmount, price) <> ((OHLCRawData.apply _).tupled, OHLCRawData.unapply)

  def pk = primaryKey("pk", (ringIndex, txHash, time))
  def idx_market_hash = index("idx_market_hash", (marketHash), unique = false)

  def idx_market_hash_time =
    index("idx_market_hash_time", (marketHash, time), unique = false)
}
