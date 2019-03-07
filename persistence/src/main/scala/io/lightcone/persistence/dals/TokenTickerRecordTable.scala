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

import io.lightcone.persistence.TokenTickerRecord
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class TokenTickerRecordTable(tag: Tag)
    extends BaseTable[TokenTickerRecord](tag, "T_TOKEN_TICKER_RECORD") {

  def id = symbol
  def tokenAddress = columnAddress("token_address")
  def symbol = column[String]("symbol", O.SqlType("VARCHAR(20)"))
  def slug = column[String]("slug", O.SqlType("VARCHAR(50)"))
  def price = column[Double]("price")
  def volume24H = column[Double]("volume_24h")
  def percentChange1H = column[Double]("percent_change_1h")
  def percentChange24H = column[Double]("percent_change_24h")
  def percentChange7D = column[Double]("percent_change_7d")
  def marketCap = column[Double]("market_cap")
  def timestamp = column[Long]("timestamp")
  def isValid = column[Boolean]("is_valid")
  def dataSource = column[String]("data_source")

  // indexes
  def idx_timestamp_valid =
    index(
      "idx_timestamp_valid",
      (timestamp, isValid),
      unique = false
    )

  def pk_timestamp_slug =
    primaryKey("pk_timestamp_slug", (timestamp, slug))

  def * =
    (
      tokenAddress,
      symbol,
      slug,
      price,
      volume24H,
      percentChange1H,
      percentChange24H,
      percentChange7D,
      marketCap,
      timestamp,
      isValid,
      dataSource
    ) <> ((TokenTickerRecord.apply _).tupled, TokenTickerRecord.unapply)
}
