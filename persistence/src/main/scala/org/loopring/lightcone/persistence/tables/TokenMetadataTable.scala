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
import scala.reflect.ClassTag
import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto._
import com.google.protobuf.ByteString

class TokenMetadataTable(tag: Tag)
    extends BaseTable[XTokenMetadata](tag, "T_TOKEN_METADATA") {

  def id = address
  def address = columnAddress("address")
  def decimals = column[Int]("decimals")
  def burnRate = column[Double]("burn_rate")
  def symbol = column[String]("symbol")
  def currentPrice = column[Double]("current_price")

  def * =
    (
      address,
      decimals,
      burnRate,
      symbol,
      currentPrice
    ) <> ((XTokenMetadata.apply _).tupled, XTokenMetadata.unapply)
}
