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

class CMCTickersInUsdTable(tag: Tag)
    extends BaseTable[CMCTickersInUsd](tag, "T_CMC_TICKERS_IN_USD") {

  def id = ""
  def coinId = column[Int]("coin_id")
  def name = column[String]("name")
  def symbol = column[String]("symbol", O.SqlType("VARCHAR(20)"))
  def slug = column[String]("slug", O.SqlType("VARCHAR(50)"))
  def circulatingSupply = column[Double]("circulating_supply")
  def totalSupply = column[Double]("total_supply")
  def maxSupply = column[Double]("max_supply")
  def dateAdded = column[String]("date_added")
  def numMarketPairs = column[Int]("num_market_pairs")
  def cmcRank = column[Int]("cmc_rank")
  def rankLastUpdated = column[String]("rank_last_updated")

  // usd_quote
  def price = column[Double]("price")
  def volume24H = column[Double]("volume_24h")
  def percentChange1H = column[Double]("percent_change_1h")
  def percentChange24H = column[Double]("percent_change_24h")
  def percentChange7D = column[Double]("percent_change_7d")
  def marketCap = column[Double]("market_cap")
  def quoteLastUpdated = column[String]("quote_last_updated")

  def batchId = column[Int]("batch_id")

  // indexes
  def idx_batch_id = index("idx_batch_id", (batchId), unique = false)
  def idx_batch_slug = index("idx_batch_slug", (batchId, slug), unique = true)

  def quoteProjection =
    (
      price,
      volume24H,
      percentChange1H,
      percentChange24H,
      percentChange7D,
      marketCap,
      quoteLastUpdated
    ) <> ({ tuple =>
      Option((CMCTickersInUsd.Quote.apply _).tupled(tuple))
    }, { paramsOpt: Option[CMCTickersInUsd.Quote] =>
      val params = paramsOpt.getOrElse(CMCTickersInUsd.Quote())
      CMCTickersInUsd.Quote.unapply(params)
    })

  def * =
    (
      coinId,
      name,
      symbol,
      slug,
      circulatingSupply,
      totalSupply,
      maxSupply,
      dateAdded,
      numMarketPairs,
      cmcRank,
      rankLastUpdated,
      quoteProjection,
      batchId
    ) <> ((CMCTickersInUsd.apply _).tupled, CMCTickersInUsd.unapply)
}
