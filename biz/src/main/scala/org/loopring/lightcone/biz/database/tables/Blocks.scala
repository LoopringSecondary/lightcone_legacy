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

package org.loopring.lightcone.biz.database.tables

import org.loopring.lightcone.biz.database.base._
import org.loopring.lightcone.biz.database.entity.BlockEntity
import slick.jdbc.MySQLProfile.api._

class Blocks(tag: Tag) extends BaseTable[BlockEntity](tag, "BLOCKS") {
  def blockNumber = column[Long]("block_number", O.SqlType("BIGINT"))
  def blockHash = column[String]("block_hash", O.SqlType("VARCHAR(82)"))
  def parentHash = column[String]("parent_hash", O.SqlType("VARCHAR(82)"))
  def fork = column[Boolean]("fork", O.SqlType("TINYINT(4)"))

  def * = (
    id,
    createdAt,
    updatedAt,
    blockHash,
    blockNumber,
    parentHash,
    fork
  ) <> ((BlockEntity.apply _).tupled, BlockEntity.unapply)

}
