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

class MarketConfigTable(tag: Tag)
    extends BaseTable[MarketConfig](tag, "T_MARKET_CONFIG") {

  def id = marketHash
  def marketHash = columnAddress("market_hash", O.PrimaryKey)
  def primary = columnAddress("primary")
  def secondary = columnAddress("secondary")
  def maxNumbersOfOrders = column[Int]("max_numbers_of_orders")
  def priceDecimals = column[Int]("price_decimals")
  def recoverBatchSize = column[Int]("recover_batch_size")
  def levels = column[Int]("levels")
  def precisionForAmount = column[Int]("precision_for_amount")
  def precisionForTotal = column[Int]("precision_for_total")
  def effective = column[Int]("effective")

  def idx_primary_secondary =
    index("idx_primary_secondary", (primary, secondary), unique = true)

  def marketIdProjection =
    (
      primary,
      secondary
    ) <> ({ tuple =>
      Option((MarketId.apply _).tupled(tuple))
    }, { paramsOpt: Option[MarketId] =>
      val params = paramsOpt.getOrElse(MarketId())
      MarketId.unapply(params)
    })

  def * =
    (
      marketIdProjection,
      marketHash,
      maxNumbersOfOrders,
      priceDecimals,
      recoverBatchSize,
      levels,
      precisionForAmount,
      precisionForTotal,
      effective
    ) <> ((MarketConfig.apply _).tupled, MarketConfig.unapply)
}
