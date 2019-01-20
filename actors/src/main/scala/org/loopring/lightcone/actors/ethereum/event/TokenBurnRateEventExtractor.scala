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

package org.loopring.lightcone.actors.ethereum.event

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.proto._
import scala.collection.JavaConverters._
import scala.concurrent._

class TokenBurnRateEventExtractor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext)
    extends EventExtractor[TokenBurnRateChangedEvent] {

  val rateMap = config
    .getConfigList("loopring_protocol.burn-rate-table.tiers")
    .asScala
    .map(config => {
      val key = config.getInt("tier")
      val rateConfig = config.getConfig("rate")
      val rate = rateConfig.getInt("market") -> rateConfig.getInt("p2p")
      key -> rate
    })
    .toMap
  val base = config.getInt("loopring_protocol.burn-rate-table.base")

  def extract(block: RawBlockData): Future[Seq[TokenBurnRateChangedEvent]] =
    Future {
      (block.txs zip block.receipts).flatMap {
        case (tx, receipt) =>
          val header = getEventHeader(tx, receipt, block.timestamp)
          receipt.logs.zipWithIndex.map {
            case (log, index) =>
              loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: TokenTierUpgradedEvent.Result) =>
                  val rate = rateMap(event.tier.intValue())
                  Some(
                    TokenBurnRateChangedEvent(
                      header = Some(header.withLogIndex(index)),
                      token = event.add,
                      burnRate = BurnRate(
                        forMarket = rate._1 / base.doubleValue(),
                        forP2P = rate._2 / base.doubleValue()
                      )
                    )
                  )
                case _ =>
                  None
              }
          }.filter(_.nonEmpty).map(_.get)

      }
    }
}
