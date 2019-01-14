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

import org.loopring.lightcone.persistence.base.BaseDalImpl
import org.loopring.lightcone.persistence.tables.MarketMetadataTable
import org.loopring.lightcone.proto._
import scala.concurrent.Future

trait MarketMetadataDal
    extends BaseDalImpl[MarketMetadataTable, MarketMetadata] {

  def saveMarket(marketMetadata: MarketMetadata): Future[ErrorCode]

  def saveMarkets(marketMetadatas: Seq[MarketMetadata]): Future[Seq[String]]

  def updateMarket(marketMetadata: MarketMetadata): Future[ErrorCode]

  def getMarkets(
      reloadFromDatabase: Boolean = false
    ): Future[Seq[MarketMetadata]]

  def getMarketsByHashes(
      marketsHashes: Seq[String]
    ): Future[Seq[MarketMetadata]]

  def disableMarketByHash(marketHash: String): Future[ErrorCode]
}
