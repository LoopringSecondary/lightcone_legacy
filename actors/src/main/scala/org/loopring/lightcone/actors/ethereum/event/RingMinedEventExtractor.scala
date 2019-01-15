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
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{RingMinedEvent => PRingMinedEvent, _}
import org.web3j.utils.Numeric
import scala.concurrent._
import org.loopring.lightcone.actors.data._

class RingMinedEventExtractor @Inject()(implicit val ec: ExecutionContext)
    extends EventExtractor[PRingMinedEvent] {

  val fillLength: Int = 8 * 64

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Future[Seq[PRingMinedEvent]] = Future {
    val header = getEventHeader(tx, receipt, blockTime)
    if (isSucceed(receipt.status)) {
      receipt.logs.zipWithIndex.map { item =>
        {
          val (log, index) = item
          loopringProtocolAbi
            .unpackEvent(log.data, log.topics.toArray) match {
            case Some(event: RingMinedEvent.Result) =>
              val fillContent =
                Numeric.cleanHexPrefix(event._fills).substring(128)
              val orderFilledEvents =
                (0 until (fillContent.length / fillLength)).map { index =>
                  fillContent.substring(
                    index * fillLength,
                    fillLength * (index + 1)
                  ) -> index
                }.map {
                  case (fill, eventIndex) =>
                    fillToOrderFilledEvent(
                      fill,
                      event,
                      receipt,
                      Some(
                        header.copy(
                          logIndex = index,
                          eventIndex = eventIndex
                        )
                      )
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
            case _ =>
              None
          }
        }
      }.filter(_.nonEmpty).map(_.get)
    } else {
      ringSubmitterAbi.unpackFunctionInput(tx.input) match {
        case Some(params: SubmitRingsFunction.Params) =>
          val ringData = params.data
          //TODO (yadong) 等待孔亮的提供具体的解析方法
          Seq.empty
        case _ =>
          Seq.empty
      }
    }
  }
  private def fillToOrderFilledEvent(
      fill: String,
      event: RingMinedEvent.Result,
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): OrderFilledEvent = {
    val data = Numeric.cleanHexPrefix(fill)
    OrderFilledEvent(
      header,
      orderHash = Numeric.prependHexPrefix(data.substring(0, 64 * 1)),
      owner = Address(data.substring(64 * 1, 64 * 2)).toString,
      tokenS = Address(fill.substring(64 * 2, 64 * 3)).toString,
      ringHash = event._ringHash,
      ringIndex = event._ringIndex.longValue(),
      filledAmountS =
        Numeric.toBigInt(data.substring(64 * 3, 64 * 4)).toByteArray,
      filledAmountFee = Numeric
        .toBigInt(data.substring(64 * 5, 64 * 6))
        .toByteArray,
      feeAmountS = Numeric
        .toBigInt(data.substring(64 * 6, 64 * 7))
        .toByteArray,
      feeAmountB = Numeric
        .toBigInt(data.substring(64 * 7, 64 * 8))
        .toByteArray,
      feeRecipient = event._feeRecipient
    )
  }

}
