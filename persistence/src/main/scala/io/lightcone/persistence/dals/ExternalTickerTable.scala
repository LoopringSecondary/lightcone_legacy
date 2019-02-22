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

class ExternalTickerTable(tag: Tag)
    extends BaseTable[ExternalTicker](tag, "T_EXTERNAL_TICKER") {

  def id = slug
  def slug = column[String]("slug", O.SqlType("VARCHAR(50)"))

  // usd_quote
  def priceUsd = column[Double]("price_usd")
  def volume24H = column[Double]("volume_24h")
  def percentChange1H = column[Double]("percent_change_1h")
  def percentChange24H = column[Double]("percent_change_24h")
  def percentChange7D = column[Double]("percent_change_7d")
  def marketCapUsd = column[Double]("market_cap_usd")

  def timestamp = column[Long]("timestamp")
  def isEffective = column[Boolean]("is_effective")

  // indexes
  def idx_request_time =
    index(
      "idx_request_time",
      (timestamp),
      unique = false
    )

  def idx_request_time_effective =
    index(
      "idx_request_time_effective",
      (timestamp, isEffective),
      unique = false
    )

  def pk_request_time_slug =
    primaryKey("pk_request_time_slug", (timestamp, slug))

  def quoteProjection =
    (
      priceUsd,
      volume24H,
      percentChange1H,
      percentChange24H,
      percentChange7D,
      marketCapUsd
    ) <> ({ tuple =>
      Option((ExternalTicker.Ticker.apply _).tupled(tuple))
    }, { paramsOpt: Option[ExternalTicker.Ticker] =>
      val params = paramsOpt.getOrElse(ExternalTicker.Ticker())
      ExternalTicker.Ticker.unapply(params)
    })

  def * =
    (
      slug,
      quoteProjection,
      timestamp,
      isEffective
    ) <> ((ExternalTicker.apply _).tupled, ExternalTicker.unapply)
}
