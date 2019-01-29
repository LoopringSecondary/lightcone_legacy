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
import org.loopring.lightcone.ethereum.data._
import org.loopring.lightcone.proto.{
  OrdersCancelledEvent => POrdersCancelledEvent,
  _
}

import scala.concurrent._

class OrdersCancelledEventExtractor @Inject() (
  implicit val ec: ExecutionContext,
  config: Config)
  extends EventExtractor[POrdersCancelledEvent] {

  val orderCancelAddress = Address(
    config.getString("loopring_protocol.order-cancel-address")).toString()

  def extract(block: RawBlockData): Future[Seq[POrdersCancelledEvent]] =
    Future {
      (block.txs zip block.receipts).flatMap {
        case (tx, receipt) if tx.to.equalsIgnoreCase(orderCancelAddress) =>
          val header = getEventHeader(tx, receipt, block.timestamp)
          receipt.logs.zipWithIndex.map {
            case (log, index) =>
              loopringProtocolAbi
                .unpackEvent(log.data, log.topics.toArray) match {
                  case Some(event: OrdersCancelledEvent.Result) =>
                    Some(
                      POrdersCancelledEvent(
                        header = Some(header.withLogIndex(index)),
                        broker = Address.normalize(event.address),
                        orderHashes = event._orderHashes,
                        owner = Address.normalize(event.address)))
                  case _ =>
                    None

                }
          }.filter(_.nonEmpty).map(_.get)
        case _ => Seq.empty
      }
    }
}
