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

import com.google.inject.Inject
import io.lightcone.core.ErrorCode
import io.lightcone.ethereum.persistence.{
  GetMarketHistory,
  OHLCData,
  OHLCRawData
}
import io.lightcone.lib.cache._
import io.lightcone.persistence.dals._
import scala.concurrent.{ExecutionContext, Future}

class OHLCDataServiceImpl @Inject()(
    implicit
    basicCache: Cache[String, Array[Byte]],
    ohlcDataDal: OHLCDataDal,
    val ec: ExecutionContext)
    extends OHLCDataService {

  def saveData(data: OHLCRawData): Future[(ErrorCode, Option[OHLCRawData])] =
    ohlcDataDal.saveData(data)

  def getOHLCData(
      marketHash: String,
      interval: GetMarketHistory.Interval,
      beginTime: Long,
      endTime: Long
    ): Future[Seq[OHLCData]] = {
    ohlcDataDal
      .getOHLCData(
        marketHash,
        interval.value,
        beginTime,
        endTime
      )
      .map(r => r.map(t => OHLCData(data = t)))
  }
}
