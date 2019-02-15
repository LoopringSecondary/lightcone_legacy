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

import io.lightcone.persistence._
import io.lightcone.persistence.base._
import slick.jdbc.MySQLProfile.api._

class TokenTickerInfoTable(tag: Tag)
    extends BaseTable[TokenTickerInfo](tag, "T_TOKEN_TICKER_INFO") {

  def id = tokenId.toString
  def tokenId = column[Int]("token_id")
  def name = column[String]("name")
  def symbol = column[String]("symbol", O.SqlType("VARCHAR(30)"))
  def websiteSlug = column[String]("website_slug")
  def market = column[String]("market", O.SqlType("VARCHAR(30)"))
  def rank = column[Int]("rank")
  def circulatingSupply = column[Double]("circulating_supply")
  def totalSupply = column[Double]("total_supply")
  def maxSupply = column[Double]("max_supply")
  def price = column[Double]("price")
  def volume24H = column[Double]("volume_24h")
  def marketCap = column[Double]("market_cap")
  def percentChange1H = column[Double]("percent_change_1h")
  def percentChange24H = column[Double]("percent_change_24h")
  def percentChange7D = column[Double]("percent_change_7d")
  def lastUpdated = column[Long]("last_updated")
  def pair = column[String]("pair", O.SqlType("VARCHAR(30)"), O.PrimaryKey)

  // indexes
  def idx_symbol = index("idx_symbol", (symbol), unique = false)
  def idx_market = index("idx_market", (market), unique = false)

  def * =
    (
      tokenId,
      name,
      symbol,
      websiteSlug,
      market,
      rank,
      circulatingSupply,
      totalSupply,
      maxSupply,
      price,
      volume24H,
      marketCap,
      percentChange1H,
      percentChange24H,
      percentChange7D,
      lastUpdated,
      pair
    ) <> ((TokenTickerInfo.apply _).tupled, TokenTickerInfo.unapply)
}
