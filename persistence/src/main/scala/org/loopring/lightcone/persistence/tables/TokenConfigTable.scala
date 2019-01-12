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

class TokenConfigTable(tag: Tag)
    extends BaseTable[TokenConfig](tag, "T_TOKEN_CONFIG") {

  def id = address
  def address = columnAddress("address", O.PrimaryKey)
  def symbol = column[String]("symbol")
  def decimals = column[Int]("decimals")
  def name = column[String]("name")
  def unit = column[String]("unit")
  def website = column[String]("website")
  def precision = column[Int]("precision")
  def isSupportTransfer = column[Boolean]("is_support_transfer")
  def isSupportTrade = column[Boolean]("is_support_trade")
  def burnRate = column[Double]("burn_rate")
  def currentPrice = column[Double]("current_price")
  def createdAt = column[Long]("created_at")
  def updatedAt = column[Long]("updated_at")

  def metadataProjection =
    (
      burnRate,
      currentPrice
    ) <> ({ tuple =>
      Option((TokenConfig.Metadata.apply _).tupled(tuple))
    }, { paramsOpt: Option[TokenConfig.Metadata] =>
      val params = paramsOpt.getOrElse(TokenConfig.Metadata())
      TokenConfig.Metadata.unapply(params)
    })

  def * =
    (
      address,
      symbol,
      decimals,
      name,
      unit,
      website,
      precision,
      isSupportTransfer,
      isSupportTrade,
      metadataProjection,
      createdAt,
      updatedAt
    ) <> ((TokenConfig.apply _).tupled, TokenConfig.unapply)
}
