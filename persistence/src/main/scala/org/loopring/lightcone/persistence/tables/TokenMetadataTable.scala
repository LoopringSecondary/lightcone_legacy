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

// message TokenMetadata {
//     enum Type {
//         TOKEN_TYPE_ERC20       = 0;
//         TOKEN_TYPE_ERC1400     = 1;
//     }

//     enum Status {
//         DISABLED    = 0; // This token is NOT displyed in walelt
//         ENABLED     = 1;  // This token is displayed in wallet.
//     }

// Type     type                   = 1;
// Status   status                 = 2;
// string   symbol                 = 3;
// string   name                   = 4;
// string   address                = 5;
// string   unit                   = 6;
// int32    decimals               = 7;
// string   website_url            = 8;
// int32    precision              = 9;
// double   burn_rate              = 10;
// double   usd_price              = 11;
// int64    updated_at             = 12;
// }

class TokenMetadataTable(tag: Tag)
    extends BaseTable[TokenMetadata](tag, "T_TOKEN_METADATA") {

  implicit val typeColumnType = enumColumnType(TokenMetadata.Type)
  implicit val statusColumnType = enumColumnType(TokenMetadata.Status)

  def id = address

  def `type` = column[TokenMetadata.Type]("type")
  def status = column[TokenMetadata.Status]("status")
  def symbol = column[String]("symbol")
  def name = column[String]("name")
  def address = columnAddress("address")
  def unit = column[String]("unit")
  def decimals = column[Int]("decimals")
  def websiteUrl = column[String]("website_url")
  def precision = column[Int]("precision")
  def burnRate = column[Double]("burn_rate")
  def usdPrice = column[Double]("usd_price")
  def updateAt = column[Long]("update_at")

  def idx_type = index("idx_type", (`type`), unique = false)
  def idx_status = index("idx_status", (status), unique = false)
  def idx_symbol = index("idx_symbol", (symbol), unique = true)
  def idx_name = index("idx_name", (name), unique = true)
  def idx_address = index("idx_address", (address), unique = true)

  def * =
    (
      `type`,
      status,
      symbol,
      name,
      address,
      unit,
      decimals,
      websiteUrl,
      precision,
      burnRate,
      usdPrice,
      updateAt
    ) <> ((TokenMetadata.apply _).tupled, TokenMetadata.unapply)
}
