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

package org.loopring.lightcone.persistence.service

import com.google.inject.Inject
import org.loopring.lightcone.persistence.dals.MarketMetadataDal
import org.loopring.lightcone.proto.{ErrorCode, MarketId, MarketMetadata}
import scala.concurrent.{ExecutionContext, Future}

class MarketMetadataServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    marketMetadataDal: MarketMetadataDal)
    extends MarketMetadataService {

  def saveMarket(marketMetadata: MarketMetadata): Future[ErrorCode] =
    marketMetadataDal.saveMarket(marketMetadata)

  def saveMarkets(marketMetadatas: Seq[MarketMetadata]): Future[Seq[String]] =
    marketMetadataDal.saveMarkets(marketMetadatas)

  def updateMarket(marketMetadata: MarketMetadata): Future[ErrorCode] =
    marketMetadataDal.updateMarket(marketMetadata)

  def getMarkets(reloadFromDatabase: Boolean): Future[Seq[MarketMetadata]] =
    marketMetadataDal.getMarkets(reloadFromDatabase)

  def getMarketsByHash(marketHashes: Seq[String]): Future[Seq[MarketMetadata]] =
    marketMetadataDal.getMarketsByHashes(marketHashes)

  def getMarketsById(marketIds: Seq[MarketId]): Future[Seq[MarketMetadata]] =
    marketMetadataDal.getMarketsByIds(marketIds)

  def disableMarketById(market: MarketId): Future[ErrorCode] =
    marketMetadataDal.disableMarketById(market)

  def disableMarketByHash(marketHash: String): Future[ErrorCode] =
    marketMetadataDal.disableMarketByHash(marketHash)
}
