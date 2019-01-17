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

import org.loopring.lightcone.proto.{OHLCRawData, PersistRawData}
import org.loopring.lightcone.proto.ErrorCode.ERR_NONE

import scala.concurrent.Await
import scala.concurrent.duration._

class OHLCDataDalSpec extends DalPostgreSpec[OHLCDataDal] {
  def getDal = new OHLCDataDalImpl()

  "saveOHLCRawData" must "save a OHLCRawData with ringIndex 1000" in {
    println("saveRawData")
    val data = OHLCRawData(
      ringIndex = 1000,
      txHash =
        "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf51",
      marketId = "111222",
      dealtAt = 1547049600000L,
      volumeA = 2.5,
      volumeB = 1000,
      price = 3.5
    )
    val result = dal.saveRawData(data)
    val res = Await.result(result.mapTo[PersistRawData.Res], 5.second)
    res.error should be(ERR_NONE)
    res.record.get should be(data)
  }
}
