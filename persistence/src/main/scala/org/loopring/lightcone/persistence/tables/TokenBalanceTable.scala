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

// message XTokenBalance {
//     int64  id                   = 1;
//     string address              = 2;
//     string token                = 3;
//     bytes  balance              = 4;
//     bytes  allowance            = 5;
//     int64  updated_at_block     = 7;
// }

package org.loopring.lightcone.persistence.tables

import org.loopring.lightcone.persistence.base._
import scala.reflect.ClassTag
import slick.jdbc.MySQLProfile.api._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.ethereum._
import com.google.protobuf.ByteString

class TokenBalanceTable(tag: Tag)
  extends BaseTable[XTokenBalance](tag, "T_TOKEN_BALANCES") {

  // Do not support id-based operations
  def id = throw new UnsupportedOperationException(
    s"${getClass.getName} does not support id-based operations"
  )

  def address = columnAddress("address")
  def token = columnAddress("address")
  def balance = columnAmount("balance")
  def allowance = columnAmount("allowance")
  def updatedAtBlock = column[Long]("updated_at_block")

  // indexes
  def idx_address = index("idx_address", (address), unique = false)
  def idx_token = index("idx_token", (token), unique = false)
  def pk = primaryKey("pk", (address, token))

  def * = (
    address,
    token,
    balance,
    allowance,
    updatedAtBlock
  ) <> ((XTokenBalance.apply _).tupled, XTokenBalance.unapply)
}
