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
import io.lightcone.core.MarketHash
import io.lightcone.lib.cache._
import io.lightcone.persistence.dals._
import io.lightcone.relayer.data._

import scala.concurrent.{ExecutionContext, Future}

class OHLCDataServiceImpl @Inject()(
    implicit
    basicCache: Cache[String, Array[Byte]],
    ohlcDataDal: OHLCDataDal,
    val ec: ExecutionContext)
    extends OHLCDataService {

  private val cache = ProtoCache[GetMarketHistory.Res]("ohlc")

  def saveData(req: PersistOHLCData.Req): Future[PersistOHLCData.Res] =
    ohlcDataDal.saveData(req.data.get)

  def getOHLCData(request: GetMarketHistory.Req): Future[GetMarketHistory.Res] =
    cache
      .read(request, 60 /*seconds*/ ) {
        ohlcDataDal
          .getOHLCData(
            MarketHash(request.getMarketPair).hashString(),
            request.interval.value,
            request.beginTime,
            request.endTime
          )
          .map { r =>
            Option(GetMarketHistory.Res(data = r.map(t => OHLCData(data = t))))
          }
      }
      .map(_.getOrElse(GetMarketHistory.Res()))
}
