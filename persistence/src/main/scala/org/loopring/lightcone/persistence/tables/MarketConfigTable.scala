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
import org.loopring.lightcone.proto.MarketConfig._
import slick.jdbc.MySQLProfile.api._

class MarketConfigTable(tag: Tag)
    extends BaseTable[MarketConfig](tag, "T_MARKET_CONFIG") {
  implicit val operationStatusColumnType = enumColumnType(OperationStatus)
  implicit val metadataColumnType = enumColumnType(ViewStatus)

  def id = marketHash
  def marketHash = columnAddress("market_hash", O.PrimaryKey)

  // MarketId
  def primary = columnAddress("primary")
  def secondary = columnAddress("secondary")

  def maxNumbersOfOrders = column[Int]("max_numbers_of_orders")
  def priceDecimals = column[Int]("price_decimals")
  def recoverBatchSize = column[Int]("recover_batch_size")
  def levels = column[Int]("levels")
  def precisionForAmount = column[Int]("precision_for_amount")
  def precisionForTotal = column[Int]("precision_for_total")
  def operationStatus = column[OperationStatus]("operation_status")
  def viewStatus = column[ViewStatus]("view_status")

  // Metadata
  def numBuys = column[Int]("num_buys")
  def numSells = column[Int]("num_sells")
  def numOrders = column[Int]("num_orders")
  def bestBuyPrice = column[Double]("best_buy_price")
  def bestSellPrice = column[Double]("best_sell_price")
  def latestPrice = column[Double]("latest_price")
  def isLastTakerSell = column[Boolean]("is_last_taker_sell")

  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")

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

  def metadataProjection =
    (
      numBuys,
      numSells,
      numOrders,
      bestBuyPrice,
      bestSellPrice,
      latestPrice,
      isLastTakerSell
    ) <> ({ tuple =>
      Option((Metadata.apply _).tupled(tuple))
    }, { paramsOpt: Option[Metadata] =>
      val params = paramsOpt.getOrElse(Metadata())
      Metadata.unapply(params)
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
      operationStatus,
      viewStatus,
      metadataProjection,
      createdAt,
      updatedAt
    ) <> ((MarketConfig.apply _).tupled, MarketConfig.unapply)
}
