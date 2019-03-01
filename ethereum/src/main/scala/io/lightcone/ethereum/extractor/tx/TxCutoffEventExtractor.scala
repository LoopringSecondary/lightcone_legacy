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

package io.lightcone.ethereum.extractor

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.lib._

import scala.concurrent._

class TxCutoffEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends TxEventExtractor[CutoffEvent] {

  val orderCancelAddress = Address(
    config.getString("loopring_protocol.order-cancel-address")
  ).toString()

  def extractEvents(params: TransactionData): Future[Seq[CutoffEvent]] =
    Future {
      val (tx, receipt, eventHeader) =
        (params.tx, params.receipt, params.eventHeader)
      if (!tx.to.equalsIgnoreCase(orderCancelAddress)) {
        Seq.empty
      } else {
        receipt.logs.zipWithIndex.map {
          case (log, index) =>
            loopringProtocolAbi
              .unpackEvent(log.data, log.topics.toArray) match {
              case Some(event: AllOrdersCancelledEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(eventHeader),
                    cutoff = event._cutoff.longValue,
                    broker = Address.normalize(event._broker),
                    owner = Address.normalize(event._broker)
                  )
                )
              case Some(event: AllOrdersCancelledByBrokerEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(eventHeader),
                    cutoff = event._cutoff.longValue,
                    broker = Address.normalize(event._broker),
                    owner = Address.normalize(event._owner)
                  )
                )
              case Some(
                  event: AllOrdersCancelledForTradingPairByBrokerEvent.Result
                  ) =>
                Some(
                  CutoffEvent(
                    header = Some(eventHeader),
                    cutoff = event._cutoff.longValue,
                    broker = Address.normalize(event._broker),
                    owner = Address.normalize(event._owner),
                    marketHash = MarketHash(
                      MarketPair(event._token1, event._token2)
                    ).toString
                  )
                )
              case Some(event: AllOrdersCancelledForTradingPairEvent.Result) =>
                Some(
                  CutoffEvent(
                    header = Some(eventHeader),
                    cutoff = event._cutoff.longValue,
                    broker = Address.normalize(event._broker),
                    owner = Address.normalize(event._broker),
                    marketHash = MarketHash(
                      MarketPair(event._token1, event._token2)
                    ).toString
                  )
                )
              case _ =>
                None
            }
        }.filter(_.nonEmpty).map(_.get)
      }
    }

}
