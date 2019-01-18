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

import org.loopring.lightcone.persistence.base.BaseTable
import org.loopring.lightcone.proto.MissingBlocksRecord
import slick.jdbc.MySQLProfile.api._

class MissingBlocksRecordTable(tag: Tag)
    extends BaseTable[MissingBlocksRecord](tag, "T_MISSING_BLOCKS_RECORD") {

  def id = ""
  def blockStart = column[Long]("block_start")
  def blockEnd = column[Long]("block_end")

  def lastHandledBlock =
    column[Long]("last_handled_block")
  def sequenceId = column[Long]("sequence_id", O.PrimaryKey, O.AutoInc)

  def * =
    (
      blockStart,
      blockEnd,
      lastHandledBlock,
      sequenceId
    ) <> ((MissingBlocksRecord.apply _).tupled, MissingBlocksRecord.unapply)

}
