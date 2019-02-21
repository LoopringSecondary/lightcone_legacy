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

class ThirdPartyTokenPriceTable(tag: Tag)
    extends BaseTable[ThirdPartyTokenPrice](tag, "T_THRID_PARTY_TOKEN_PRICE") {

  def id = slug
  def slug = column[String]("slug", O.SqlType("VARCHAR(50)"))

  // usd_quote
  def price = column[Double]("price")
  def volume24H = column[Double]("volume_24h")
  def percentChange1H = column[Double]("percent_change_1h")
  def percentChange24H = column[Double]("percent_change_24h")
  def percentChange7D = column[Double]("percent_change_7d")
  def marketCap = column[Double]("market_cap")

  def requestTime = column[Long]("request_time")
  def isEffective = column[Boolean]("is_effective")

  // indexes
  def idx_request_time =
    index(
      "idx_request_time",
      (requestTime),
      unique = false
    )

  def idx_request_time_effective =
    index(
      "idx_request_time_effective",
      (requestTime, isEffective),
      unique = false
    )

  def pk_request_time_slug =
    primaryKey("pk_request_time_slug", (requestTime, slug))

  def quoteProjection =
    (
      price,
      volume24H,
      percentChange1H,
      percentChange24H,
      percentChange7D,
      marketCap
    ) <> ({ tuple =>
      Option((ThirdPartyTokenPrice.Quote.apply _).tupled(tuple))
    }, { paramsOpt: Option[ThirdPartyTokenPrice.Quote] =>
      val params = paramsOpt.getOrElse(ThirdPartyTokenPrice.Quote())
      ThirdPartyTokenPrice.Quote.unapply(params)
    })

  def * =
    (
      slug,
      quoteProjection,
      requestTime,
      isEffective
    ) <> ((ThirdPartyTokenPrice.apply _).tupled, ThirdPartyTokenPrice.unapply)
}
