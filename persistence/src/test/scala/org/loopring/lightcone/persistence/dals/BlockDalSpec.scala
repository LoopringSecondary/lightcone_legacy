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

package org.loopring.lightcone.persistence.dals

import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._
import scala.concurrent.Await
import scala.concurrent.duration._

class BlockDalSpec extends DalSpec[BlockDal] {
  import ErrorCode._

  def getDal = new BlockDalImpl()

  "saveBlock" must "save a block with hash 0x111" in {
    val block = BlockData(hash = "0x111", height = 1L)
    val result = dal.saveBlock(block)
    val res = Await.result(result.mapTo[ErrorCode], 5.second)
    res should be(ERR_NONE)
  }

  "findByHash" must "find a block with hash 0x111" in {
    val result = dal.findByHash("0x111")
    val res = Await.result(result.mapTo[Option[BlockData]], 5.second)
    res should not be empty
  }

  "findByHeight" must "find a block with height 1" in {
    val result = dal.findByHeight(1L)
    val res = Await.result(result.mapTo[Option[BlockData]], 5.second)
    res should not be empty
  }

  "findMaxHeight" must "select max height" in {
    val result = dal.findMaxHeight()
    val res = Await.result(result.mapTo[Option[Long]], 5.second)
    res should not be empty
  }

  "findBlocksInHeightRange" must "find blocks between height 1 and 10" in {
    val result = dal.findBlocksInHeightRange(1L, 10L)
    val res = Await.result(result.mapTo[Seq[(Long, String)]], 5.second)
    res should not be empty
  }

  "count" must "get saved block counts" in {
    val result = dal.count()
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be >= 0
  }

  "obsolete" must "obsolete blocks above height 1" in {
    val result = dal.obsolete(1L)
    Await.result(result.mapTo[Unit], 5.second)
  }
}
