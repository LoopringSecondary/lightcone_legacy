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

import io.lightcone.persistence.base._
import io.lightcone.ethereum.persistence._
import io.lightcone.core._
import io.lightcone.ethereum.event.BlockEvent
import io.lightcone.persistence.{Paging, SortingType}
import scala.concurrent._

trait FillDal extends BaseDalImpl[FillTable, Fill] {
  def saveFill(fill: Fill): Future[ErrorCode]
  def saveFills(fills: Seq[Fill]): Future[Seq[ErrorCode]]

  def getFills(
      owner: String,
      txHash: String,
      orderHash: String,
      ringHashOpt: Option[String],
      ringIndexOpt: Option[Long],
      fillIndexOpt: Option[Int],
      tokensOpt: Option[String],
      tokenbOpt: Option[String],
      marketHashOpt: Option[MarketHash],
      wallet: String,
      miner: String,
      sort: SortingType,
      paging: Option[Paging]
    ): Future[Seq[Fill]]

  def countFills(
      owner: String,
      txHash: String,
      orderHash: String,
      ringHashOpt: Option[String],
      ringIndexOpt: Option[Long],
      fillIndexOpt: Option[Int],
      tokensOpt: Option[String],
      tokenbOpt: Option[String],
      marketHashOpt: Option[MarketHash],
      wallet: String,
      miner: String
    ): Future[Int]

  def getMarketFills(
      marketPair: MarketPair,
      num: Int
    ): Future[Seq[Fill]]
  def cleanActivitiesForReorg(req: BlockEvent): Future[Int]
}
