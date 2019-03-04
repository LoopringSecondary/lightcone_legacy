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

package io.lightcone.ethereum.extractor.tx

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.BurnRate
import io.lightcone.ethereum.abi.{loopringProtocolAbi, TokenTierUpgradedEvent}
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor.{EventExtractor, TransactionData}
import io.lightcone.lib.{Address, NumericConversion}
import io.lightcone.relayer.data.{Transaction, TransactionReceipt}

import scala.collection.JavaConverters._
import scala.concurrent._

final class TxTokenBurnRateEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends EventExtractor[TransactionData, TokenBurnRateChangedEvent] {

  val rateMap = config
    .getConfigList("loopring_protocol.burn-rate-table.tiers")
    .asScala
    .map(conf => {
      val key = conf.getInt("tier")
      val ratesConfig = conf.getConfig("rates")
      val rates = ratesConfig.getInt("market") -> ratesConfig.getInt("p2p")
      key -> rates
    })
    .toMap
  val base = config.getInt("loopring_protocol.burn-rate-table.base")

  val burnRateAddress =
    config.getString("loopring_protocol.burnrate-table-address").toLowerCase()

  def extractEvents(txdata: TransactionData) = Future {
    if (!txdata.tx.to.equalsIgnoreCase(burnRateAddress)) {
      Seq.empty
    } else {
      txdata.receiptAndHeaderOpt match {
        case Some((receipt, eventHeader)) =>
          receipt.logs.zipWithIndex.map {
            case (log, index) =>
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                case Some(event: TokenTierUpgradedEvent.Result) =>
                  val rates = rateMap(event.tier.intValue)
                  Some(
                    TokenBurnRateChangedEvent(
                      header = Some(eventHeader),
                      token = Address.normalize(event.add),
                      burnRate = Some(
                        BurnRate(
                          forMarket = rates._1.doubleValue() / base,
                          forP2P = rates._2.doubleValue() / base
                        )
                      )
                    )
                  )
                case _ =>
                  None
              }
          }.filter(_.nonEmpty)
            .map(_.get)
        case _ => Seq.empty
      }
    }
  }
}
