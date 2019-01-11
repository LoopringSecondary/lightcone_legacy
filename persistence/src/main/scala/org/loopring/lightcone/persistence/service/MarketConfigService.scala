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

import org.loopring.lightcone.proto._
import scala.concurrent.Future

trait MarketConfigService {
  def saveConfigs(configs: Seq[MarketConfig]): Future[Int]

  def getAllConfigs(): Future[Seq[MarketConfig]]

  def getConfig(marketHash: String): Future[Option[MarketConfig]]

  def getConfig(
      primary: String,
      secondary: String
    ): Future[Option[MarketConfig]]

  def getConfigs(marketHashes: Seq[String]): Future[Seq[MarketConfig]]

  def updateConfig(config: MarketConfig): Future[Int]
}
