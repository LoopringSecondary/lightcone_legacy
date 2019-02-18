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

package io.lightcone.core

import com.google.inject.Inject
import com.typesafe.config.Config

final class ConfigBasedMetadataManager @Inject() (implicit val config: Config)
  extends AbstractMetadataManager {

  val rateConfig = config.getConfig("loopring_protocol.default-burn-rates")

  val defaultBurnRateForMarket = rateConfig.getDouble("market-orders")
  val defaultBurnRateForP2P = rateConfig.getDouble("p2p-orders")
}
