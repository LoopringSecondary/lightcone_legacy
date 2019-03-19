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

  val marketPair = MarketPair(
    "0x2f5705b87149e017ec779707982dc4e4b58aa619",
    "0x0b44611b8ae632be05f24ffe64651f050402ae01"
  )
  val marketHash = MarketHash(marketPair).hashString

  "saveFill" must "save a trade with hash" in {
    val record0 =
      OHLCRawData(
        ringIndex = 1000,
        txHash =
          "0x5fe632ccfcc381be803617c256eff21409093c35c4e4606963be0a042384cf55",
        marketHash = marketHash,
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
      marketHash = marketHash,
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
        marketHash = marketHash,
        time = 1547682750,
        baseAmount = 100,
        quoteAmount = 500,
        price = 100
      )
    val result2 = service.saveData(record2)

    val resResult = service.getOHLCData(
      marketHash,
      Interval.OHLC_INTERVAL_ONE_MINUTES,
      1547682050,
      1547682850
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
  }
}
