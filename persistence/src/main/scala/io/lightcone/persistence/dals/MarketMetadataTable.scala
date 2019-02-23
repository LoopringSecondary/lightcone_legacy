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
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._

class MarketMetadataTable(tag: Tag)
    extends BaseTable[MarketMetadata](tag, "T_MARKET_METADATA") {

  implicit val statusColumnType = enumColumnType(MarketMetadata.Status)

  def id = marketHash

  def status = column[MarketMetadata.Status]("status")

  def quoteTokenSymbol =
    column[String]("quote_token_symbol", O.SqlType("VARCHAR(50)"))

  def baseTokenSymbol =
    column[String]("base_token_symbol", O.SqlType("VARCHAR(50)"))

  def maxNumbersOfOrders = column[Int]("max_numbers_of_orders")
  def priceDecimals = column[Int]("price_decimals")
  def orderbookAggLevels = column[Int]("orderbook_agg_levels")
  def precisionForAmount = column[Int]("precision_for_amount")
  def precisionForTotal = column[Int]("precision_for_total")
  def browsableInWallet = column[Boolean]("browsable_in_wallet")
  def updateAt = column[Long]("update_at")

  // MarketPair
  def baseToken = columnAddress("base_token")
  def quoteToken = columnAddress("quote_token")

  def marketHash = columnAddress("market_hash", O.PrimaryKey, O.Unique)

  def idx_tokens_symbol =
    index(
      "idx_tokens_symbol",
      (baseTokenSymbol, quoteTokenSymbol),
      unique = true
    )
  def idx_tokens = index("idx_tokens", (baseToken, quoteToken), unique = true)
  def idx_status = index("idx_status", (status), unique = false)

  def marketPairProjection =
    (baseToken, quoteToken) <> ({ tuple =>
      Option((MarketPair.apply _).tupled(tuple))
    }, { paramsOpt: Option[MarketPair] =>
      val params = paramsOpt.getOrElse(MarketPair())
      MarketPair.unapply(params)
    })

  def * =
    (
      status,
      quoteTokenSymbol,
      baseTokenSymbol,
      maxNumbersOfOrders,
      priceDecimals,
      orderbookAggLevels,
      precisionForAmount,
      precisionForTotal,
      browsableInWallet,
      updateAt,
      marketPairProjection,
      marketHash
    ) <> ((MarketMetadata.apply _).tupled, MarketMetadata.unapply)
}
