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

package org.loopring.lightcone.actors.base

import org.loopring.lightcone.proto.MarketMetadata
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.MetadataManager

trait MarketStatusSupport {
  actor: InitializationRetryActor =>
  val metadataManager: MetadataManager

  metadataManager.subscribMarket(processMarketmetaChange)

  def processMarketmetaChange(marketMetadata: MarketMetadata): Unit

}
