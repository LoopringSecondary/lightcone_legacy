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

package io.lightcone.persistence.dals

import io.lightcone.core._
import scala.concurrent.Await
import scala.concurrent.duration._

// TODO(yongfeng): remove dependency to relayer.data._
import io.lightcone.relayer.data._

class OHLCDataDalSpec extends DalPostgreSpec[OHLCDataDal] {
  def getDal = new OHLCDataDalImpl()

  "saveOHLCData" must "save a OHLC raw data with ringIndex 1000" in {
    val data1 = OHLCRawData(
      ringIndex = 1000,
      txHash =
        "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf51",
      marketHash = "111222",
      time = 1547682550,
      baseAmount = 2.5,
      quoteAmount = 1000,
      price = 400
    )
    val result1 = dal.saveData(data1)
    val res1 = Await.result(result1.mapTo[PersistOHLCData.Res], 5.second)
    res1.error should be(ErrorCode.ERR_NONE)
    res1.record.get should be(data1)

    val data2 = OHLCRawData(
      ringIndex = 1001,
      txHash =
        "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf55",
      marketHash = "111222",
      time = 1547682650,
      baseAmount = 50,
      quoteAmount = 500,
      price = 50
    )
    val result2 = dal.saveData(data2)
    val res2 = Await.result(result2.mapTo[PersistOHLCData.Res], 5.second)
    res2.error should be(ErrorCode.ERR_NONE)
    res2.record.get should be(data2)

    val data3 = OHLCRawData(
      ringIndex = 1002,
      txHash =
        "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf65",
      marketHash = "111222",
      time = 1547682675,
      baseAmount = 10.5,
      quoteAmount = 1050,
      price = 100
    )
    val result3 = dal.saveData(data3)
    val res3 = Await.result(result3.mapTo[PersistOHLCData.Res], 5.second)
    res3.error should be(ErrorCode.ERR_NONE)
    res3.record.get should be(data3)

    val marketHash = "111222"
    val interval = 50
    val beginTime = 1547682050
    val endTime = 1547682850
    val queryResult = dal.getOHLCData(marketHash, interval, beginTime, endTime)
    val queryRes = Await.result(queryResult.mapTo[Seq[Seq[Double]]], 5.second)
    queryRes.length == 2 should be(true)
    val array0 = queryRes(0)
    assert(
      array0(0).toLong == 1547682650 &&
        array0(1) == 60.5 &&
        array0(2) == 1550.0 &&
        array0(3) == 50.0 &&
        array0(4) == 100.0 &&
        array0(5) == 100.0 &&
        array0(6) == 50.0
    )
  }
}
