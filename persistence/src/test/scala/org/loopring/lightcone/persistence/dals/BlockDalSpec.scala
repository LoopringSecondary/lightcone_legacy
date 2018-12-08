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

import org.loopring.lightcone.proto.ethereum.XBlockData
import org.loopring.lightcone.proto.persistence.{ Bar, XPersistenceError }
import scala.concurrent.Await
import scala.concurrent.duration._

class BlockDalSpec extends DalSpec[BlockDal] {
  def getDal = new BlockDalImpl()

  "saveBlock" must "save a block" in {
    // sbt persistence/'testOnly *BlockDalSpec -- -z saveBlock'
    val block = XBlockData(hash = "0x111", height = 1l)
    val result = dal.saveBlock(block)
    val res = Await.result(result.mapTo[XPersistenceError], 5.second)
    // res should
  }

  "findByHash" must "find a block" in {
    val result = dal.findByHash("0x111")
    val res = Await.result(result.mapTo[Option[XBlockData]], 5.second)
    // res should
  }

  "findByHeight" must "find a block" in {
    val result = dal.findByHeight(1l)
    val res = Await.result(result.mapTo[Option[XBlockData]], 5.second)
    // res should
  }

  "findMaxHeight" must "save max height" in {
    val result = dal.findMaxHeight()
    val res = Await.result(result.mapTo[Option[Long]], 5.second)
    // res should
  }

  "findBlocksInHeightRange" must "find blocks" in {
    val result = dal.findBlocksInHeightRange(1l, 10l)
    val res = Await.result(result.mapTo[Seq[(Long, String)]], 5.second)
    // res should
  }

  "count" must "get block counts" in {
    val result = dal.count()
    val res = Await.result(result.mapTo[Int], 5.second)
    // res should
  }

  "obsolete" must "obsolete blocks above height" in {
    val result = dal.obsolete(1l)
    Await.result(result.mapTo[Unit], 5.second)
    // res should
  }
}
