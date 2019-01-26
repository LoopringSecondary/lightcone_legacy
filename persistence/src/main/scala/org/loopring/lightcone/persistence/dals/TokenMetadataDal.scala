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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import scala.concurrent._

trait TokenMetadataDal extends BaseDalImpl[TokenMetadataTable, TokenMetadata] {

  def saveToken(tokenMetadata: TokenMetadata): Future[ErrorCode]

  def saveTokens(tokenMetadatas: Seq[TokenMetadata]): Future[Seq[String]]

  def updateToken(tokenMetadata: TokenMetadata): Future[ErrorCode]

  def getTokens(tokens: Seq[String]): Future[Seq[TokenMetadata]]

  def getTokens(): Future[Seq[TokenMetadata]]

  def updateBurnRate(
      token: String,
      burnRateForMarket: Double,
      burnRateForP2P: Double
    ): Future[ErrorCode]

  def InvalidateToken(address: String): Future[ErrorCode]
}
