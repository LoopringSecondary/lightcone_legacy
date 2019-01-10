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

package org.loopring.lightcone.ethereum.event

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.proto._

import scala.collection.JavaConverters._

class TokenBurnRateEventExtractor @Inject()(implicit config: Config)
    extends EventExtractor[TokenBurnRateChangedEvent] {

  val rateMap = config
    .getConfigList("loopring_protocol.burn-rate-table.tiers")
    .asScala
    .map(config => config.getInt("tier") -> config.getInt("rate"))
    .toMap
  val base = config.getInt("loopring_protocol.burn-rate-table.base")

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[TokenBurnRateChangedEvent] = ???
}
