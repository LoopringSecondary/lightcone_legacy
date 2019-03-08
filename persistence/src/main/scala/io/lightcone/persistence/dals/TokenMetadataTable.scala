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
import io.lightcone.core._

class TokenMetadataTable(tag: Tag)
    extends BaseTable[TokenMetadata](tag, "T_TOKEN_METADATA") {

  implicit val typeColumnType = enumColumnType(TokenMetadata.Type)
  implicit val statusColumnType = enumColumnType(TokenMetadata.Status)

  def id = address

  def `type` = column[TokenMetadata.Type]("type")
  def status = column[TokenMetadata.Status]("status")
  def symbol = column[String]("symbol", O.SqlType("VARCHAR(10)"))
  def name = column[String]("name", O.SqlType("VARCHAR(50)"))
  def address = columnAddress("address", O.PrimaryKey, O.Unique)
  def unit = column[String]("unit")
  def decimals = column[Int]("decimals")
  def precision = column[Int]("precision")
  def forMarket = column[Double]("for_market")
  def forP2P = column[Double]("for_p2p")
  def updateAt = column[Long]("update_at")

  def idx_type = index("idx_type", (`type`), unique = false)
  def idx_status = index("idx_status", (status), unique = false)
  def idx_symbol = index("idx_symbol", (symbol), unique = true)
  def idx_name = index("idx_name", (name), unique = true)

  def burnRateProjection =
    (
      forMarket,
      forP2P
    ) <> ({ tuple =>
      Option((BurnRate.apply _).tupled(tuple))
    }, { paramsOpt: Option[BurnRate] =>
      val params = paramsOpt.getOrElse(BurnRate())
      BurnRate.unapply(params)
    })

  def * =
    (
      `type`,
      status,
      symbol,
      name,
      address,
      unit,
      decimals,
      precision,
      burnRateProjection,
      updateAt
    ) <> ((TokenMetadata.apply _).tupled, TokenMetadata.unapply)
}
