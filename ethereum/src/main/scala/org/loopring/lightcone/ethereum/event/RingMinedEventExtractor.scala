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

import org.loopring.lightcone.ethereum.abi.RingMinedEvent
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{RingMinedEvent ⇒ PRingMinedEvent}
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

class RingMinedEventExtractor() extends DataExtractor[PRingMinedEvent] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[PRingMinedEvent] = {
    val header = getEventHeader(tx, receipt, blockTime)
    receipt.logs.zipWithIndex.map { item ⇒
      {
        val (log, index) = item
        loopringProtocolAbi
          .unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: RingMinedEvent.Result) ⇒
            val fillContent =
              Numeric.cleanHexPrefix(event._fills).substring(128)
            val fillLength = 8 * 64
            val orderFilledEvents =
              (0 until (fillContent.length / fillLength)).map { index ⇒
                fillContent.substring(
                  index * fillLength,
                  fillLength * (index + 1)
                )
              }.map { fill ⇒
                fillToOrderFilledEvent(
                  fill,
                  event,
                  receipt,
                  Some(header.withLogIndex(index))
                )
              }
            Some(
              PRingMinedEvent(
                header = Some(header.withLogIndex(index)),
                ringIndex = event._ringIndex.longValue(),
                ringHash = event._ringHash,
                fills = orderFilledEvents
              )
            )
          case _ ⇒
            None
        }
      }
    }.filter(_.nonEmpty).map(_.get)
  }

  private def fillToOrderFilledEvent(
      fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): OrderFilledEvent = {
    OrderFilledEvent(
      header = header,
      owner = Address(fill.substring(2 + 64 * 1, 2 + 64 * 2)).toString,
      orderHash = fill.substring(0, 2 + 64 * 1),
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue(),
      filledAmountS =
        Numeric.toBigInt(fill.substring(2 + 64 * 3, 2 + 64 * 4)).toByteArray,
      filledAmountFee = Numeric
        .toBigInt(fill.substring(2 + 64 * 5, 2 + 64 * 6))
        .toByteArray,
      feeAmountS = Numeric
        .toBigInt(fill.substring(2 + 64 * 6, 2 + 64 * 7))
        .toByteArray,
      feeAmountB = Numeric
        .toBigInt(fill.substring(2 + 64 * 7, 2 + 64 * 8))
        .toByteArray,
      feeRecipient = event._feeRecipient
    )
  }

}
