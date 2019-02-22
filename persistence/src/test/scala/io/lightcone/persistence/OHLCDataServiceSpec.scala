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

package io.lightcone.persistence

import io.lightcone.persistence.dals._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data.GetMarketHistory.Interval
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.Await
import scala.concurrent.duration._

class OHLCDataServiceSpec extends ServicePostgreSpec[OHLCDataService] {

  implicit var dal: OHLCDataDal = _

  def getService = {
    dal = new OHLCDataDalImpl()
    new OHLCDataServiceImpl()
  }

  def createTables(): Unit = {
    new OHLCDataDalImpl().createTable()
  }

  "saveFill" must "save a trade with hash" in {
    val record0 = PersistOHLCData.Req(
      data = Option(
        OHLCRawDataEvent(
          ringIndex = 1000,
          txHash =
            "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf55",
          marketHash = "111222",
          time = 1547682650,
          baseAmount = 10,
          quoteAmount = 100,
          price = 10
        )
      )
    )
    val result0 = service.saveData(record0)
    val res0 = Await.result(result0.mapTo[PersistOHLCData.Res], 5.second)
    res0.error should be(ErrorCode.ERR_NONE)
    res0.record.get should be(record0.data.get)

    val record1 = OHLCRawDataEvent(
      ringIndex = 1001,
      txHash =
        "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf55",
      marketHash = "111222",
      time = 1547682655,
      baseAmount = 50,
      quoteAmount = 200,
      price = 50
    )
    val result1 = dal.saveData(record1)
    val res1 = Await.result(result1.mapTo[PersistOHLCData.Res], 5.second)
    res1.error should be(ErrorCode.ERR_NONE)
    res1.record.get should be(record1)

    val record2 = PersistOHLCData.Req(
      data = Option(
        OHLCRawDataEvent(
          ringIndex = 1002,
          txHash =
            "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf50",
          marketHash = "111222",
          time = 1547682750,
          baseAmount = 100,
          quoteAmount = 500,
          price = 100
        )
      )
    )
    val result2 = service.saveData(record2)

    val request = GetMarketHistory.Req(
      marketHash = "111222",
      interval = Interval.OHLC_INTERVAL_ONE_MINUTES,
      beginTime = 1547682050,
      endTime = 1547682850
    )
    val resResult = service.getOHLCData(request)
    val res = Await.result(resResult.mapTo[GetMarketHistory.Res], 5.second)
    val singleData = res.ohlcData(1).data
    assert(
      singleData(0).toLong == 1547682600 &&
        singleData(1) == 60 &&
        singleData(2) == 300 &&
        singleData(3) == 10.0 &&
        singleData(4) == 50.0 &&
        singleData(5) == 50.0 &&
        singleData(6) == 10.0
    )
    println(res)
  }
}
