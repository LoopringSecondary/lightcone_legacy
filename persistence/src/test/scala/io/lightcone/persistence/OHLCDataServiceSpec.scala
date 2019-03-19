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
import io.lightcone.ethereum.persistence._
import io.lightcone.core._
import io.lightcone.ethereum.persistence.GetMarketHistory.Interval
import io.lightcone.lib.cache._
import org.slf4s.Logging

import scala.concurrent.Await
import scala.concurrent.duration._

class OHLCDataServiceSpec
    extends ServicePostgreSpec[OHLCDataService]
    with Logging {

  implicit var dal: OHLCDataDal = _

  implicit val cache = new NoopCache[String, Array[Byte]]

  def getService = {
    dal = new OHLCDataDalImpl()
    new OHLCDataServiceImpl()
  }

  def createTables(): Unit = {
    new OHLCDataDalImpl().createTable()
  }

  "saveFill" must "save a trade with hash" in {
    val record0 =
      OHLCRawData(
        ringIndex = 1000,
        txHash =
          "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf55",
        marketHash = "111222",
        time = 1547682650,
        baseAmount = 10,
        quoteAmount = 100,
        price = 10
      )

    val result0 = service.saveData(record0)
    val res0 =
      Await.result(result0.mapTo[(ErrorCode, Option[OHLCRawData])], 5.second)
    res0._1 should be(ErrorCode.ERR_NONE)
    res0._2.get should be(record0)

    val record1 = OHLCRawData(
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
    val res1 =
      Await.result(result1.mapTo[(ErrorCode, Option[OHLCRawData])], 5.second)
    res1._1 should be(ErrorCode.ERR_NONE)
    res1._2.get should be(record1)

    val record2 =
      OHLCRawData(
        ringIndex = 1002,
        txHash =
          "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf50",
        marketHash = "111222",
        time = 1547682750,
        baseAmount = 100,
        quoteAmount = 500,
        price = 100
      )
    val result2 = service.saveData(record2)

    val request = GetMarketHistory.Req(
      marketHash = "111222",
      interval = Interval.OHLC_INTERVAL_ONE_MINUTES,
      beginTime = 1547682050,
      endTime = 1547682850
    )
    val resResult = service.getOHLCData(
      request.marketHash,
      request.interval,
      request.beginTime,
      request.endTime
    )
    val res = Await.result(resResult.mapTo[Seq[OHLCData]], 5.second)
    val singleData = res(1).data
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

    val res2 = service.getOHLCData(
      "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
      request.interval,
      request.beginTime,
      request.endTime
    )
    val r2 = Await.result(res2.mapTo[Seq[OHLCData]], 5.second)
    log.info(s"---3 $r2")

    val res3 = service.getOHLCData(
      "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
      request.interval,
      request.beginTime,
      request.endTime
    )
    val r3 = Await.result(res3.mapTo[Seq[OHLCData]], 5.second)
    log.info(s"---3 $r3")
  }
}
