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

package io.lightcone.relayer.ethereum.event

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.lib._
import io.lightcone.ethereum.abi._
import io.lightcone.relayer.data._
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.event.TokenBurnRateChangedEvent._

import scala.collection.JavaConverters._
import scala.concurrent._

class TokenBurnRateEventExtractor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext)
    extends AbstractEventExtractor {

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

  //TODO: 需要验证地址
  def extractEventsFromTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]] = Future {
    receipt.logs.zipWithIndex.map {
      case (log, index) =>
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: TokenTierUpgradedEvent.Result) =>
            val rates = rateMap(event.tier.intValue)
            Some(
              TokenBurnRateChangedEvent(
                header = Some(eventHeader.withLogIndex(index)),
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
    }.filter(_.nonEmpty).map(_.get)
  }

}
