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

import org.loopring.lightcone.proto.{ErrorCode, MarketId, MarketMetadata}
import scala.concurrent.Future

trait MarketMetadataService {

  def saveMarket(marketMetadata: MarketMetadata): Future[ErrorCode]

  def saveMarkets(marketMetadatas: Seq[MarketMetadata]): Future[Seq[String]]

  def updateMarket(marketMetadata: MarketMetadata): Future[ErrorCode]

  def getMarkets(
      reloadFromDatabase: Boolean = false
    ): Future[Seq[MarketMetadata]]

  def getMarketsByHash(marketHashes: Seq[String]): Future[Seq[MarketMetadata]]

  def getMarketsById(marketIds: Seq[MarketId]): Future[Seq[MarketMetadata]]

  def disableMarketById(market: MarketId): Future[ErrorCode]

  def disableMarketByHash(marketHash: String): Future[ErrorCode]

}
