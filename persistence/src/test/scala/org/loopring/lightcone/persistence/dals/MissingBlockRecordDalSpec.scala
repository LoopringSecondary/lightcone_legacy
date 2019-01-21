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
import scala.concurrent.Await
import scala.concurrent.duration._

class MissingBlockRecordDalSpec extends DalSpec[MissingBlocksRecordDal] {
  def getDal = new MissingBlocksRecordDalImpl()

  "missing block record spec" must "save missing blocks successfuly" in {
    info("save 3 records")
    val r1 =
      dal.saveMissingBlock(MissingBlocksRecord(blockStart = 1L, blockEnd = 100))
    val res1 = Await.result(r1.mapTo[Long], 5 second)
    assert(res1 > 0)
    val r2 =
      dal.saveMissingBlock(MissingBlocksRecord(blockStart = 2L, blockEnd = 100))
    val res2 = Await.result(r2.mapTo[Long], 5 second)
    assert(res2 > 0)
    val r3 =
      dal.saveMissingBlock(MissingBlocksRecord(blockStart = 3L, blockEnd = 200))
    val res3 = Await.result(r3.mapTo[Long], 5 second)
    assert(res3 > 0)

    info("query the oldest one")
    val r4 = dal.getOldestOne()
    val res4 = Await
      .result(r4.mapTo[Option[MissingBlocksRecord]], 5 second)
      .getOrElse(MissingBlocksRecord())
    assert(
      res4.sequenceId == res1 && res4.blockStart == 1 && res4.blockEnd == 100 && res4.lastHandledBlock == 0
    )

    info("update handle progress of the oldest one")
    val r5 = dal.updateProgress(res4.sequenceId, 50)
    val res5 = Await.result(r5.mapTo[Int], 5 second)
    assert(res5 == 1)
    val r6 = dal.getOldestOne()
    val res6 = Await
      .result(r6.mapTo[Option[MissingBlocksRecord]], 5 second)
      .getOrElse(MissingBlocksRecord())
    assert(
      res6.sequenceId == res1 && res6.blockStart == 1 && res6.blockEnd == 100 && res6.lastHandledBlock == 50
    )

    info("delete the oldest as handled")
    val r7 = dal.deleteRecord(res4.sequenceId)
    val res7 = Await.result(r7.mapTo[Boolean], 5 second)
    assert(res7)

    info("query the oldest one second time")
    val r8 = dal.getOldestOne()
    val res8 = Await
      .result(r8.mapTo[Option[MissingBlocksRecord]], 5 second)
      .getOrElse(MissingBlocksRecord())
    assert(
      res8.sequenceId == res2 && res8.blockStart == 2 && res8.blockEnd == 100 && res8.lastHandledBlock == 0
    )
  }
}
