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

package io.lightcone.actors.support

import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.native.JsonMethods.parse

import io.lightcone.proto._
import io.lightcone.core._
import scalapb.json4s.JsonFormat
import org.web3j.utils.Numeric

import scala.io.Source

trait EventExtractorSupport {
  implicit val formats = DefaultFormats

  val resStr: String = Source
    .fromFile("actors/src/test/resources/event/block")
    .getLines()
    .next()

  val blockRes =
    JsonFormat.fromJsonString[GetBlockWithTxObjectByNumber.Res](resStr)

  val block = blockRes.result.get

  val receiptStr = Source
    .fromFile("actors/src/test/resources/event/receipts")
    .getLines()
    .next()

  val resps = parse(receiptStr).values.asInstanceOf[List[Map[String, Any]]]

  val receiptResps = resps.map(resp => {
    val respJson = Serialization.write(resp)
    JsonFormat.fromJsonString[GetTransactionReceipt.Res](respJson)
  })

  val blockData = RawBlockData(
    height = Numeric.toBigInt(formatHex(block.number)).longValue(),
    hash = block.hash,
    txs = block.transactions,
    uncles = block.uncles,
    miner = block.miner,
    timestamp = block.timestamp,
    receipts = receiptResps.map(_.result.get)
  )

  private class EmptyValueSerializer
      extends CustomSerializer[String](
        _ =>
          ({
            case JNull => ""
          }, {
            case "" => JNothing
          })
      )

}
