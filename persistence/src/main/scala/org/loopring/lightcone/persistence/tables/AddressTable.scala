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
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto._
import com.google.protobuf.ByteString

class AddressTable(tag: Tag)
  extends BaseTable[XAddressData](tag, "T_ADDRESSES") {

  def id = address
  def address = columnAddress("address", O.PrimaryKey)
  def balance = columnAmount("balance")
  def numTx = column[Long]("num_tx")
  def creatorAddress = columnAddress("creator_address")
  def creatorTx = columnHash("creator_tx")
  def updatedAtBlock = column[Long]("updated_at_block")

  // indexes
  def idx_updated_at_block = index("idx_updated_at_block", (updatedAtBlock), unique = false)

  def * = (
    address,
    balance,
    numTx,
    creatorAddress,
    creatorTx,
    updatedAtBlock
  ) <> ((XAddressData.apply _).tupled, XAddressData.unapply)
}
