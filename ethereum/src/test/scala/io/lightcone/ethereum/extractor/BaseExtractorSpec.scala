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

import io.lightcone.relayer.data._
import io.lightcone.ethereum.abi._
import org.json4s.{CustomSerializer, DefaultFormats, JNothing, JNull}
import org.json4s.native.JsonMethods.parse
import org.scalatest._
import scalapb.json4s.JsonFormat

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source

class BaseExtractorSpec extends FlatSpec with Matchers {

  val ercAbi = erc20Abi
  implicit val formats = DefaultFormats

  val resStr: String = Source
    .fromFile("ethereum/src/test/resources/event/block")
    .getLines()
    .next()

  val s = Source
    .fromResource("version2.0/WETH.abi")
    .mkString
  val wethAbi1 = wethAbi

  val blockRes =
    JsonFormat.fromJsonString[GetBlockWithTxObjectByNumber.Res](resStr)

  val block = blockRes.result.get

  val receiptStr = Source
    .fromFile("ethereum/src/test/resources/event/receipts")
    .getLines()
    .next()

  val resps = parse(receiptStr).values.asInstanceOf[List[Map[String, Any]]]

  val ser = org.json4s.jackson.Serialization

  val receiptResps = resps.map(resp => {
    val respJson = ser.write(resp)
    JsonFormat.fromJsonString[GetTransactionReceipt.Res](respJson)
  })

  val blockData = block.withReceipts(receiptResps.map(_.result.get))

  private class EmptyValueSerializer
      extends CustomSerializer[String](
        _ =>
          ({
            case JNull => ""
          }, {
            case "" => JNothing
          })
      )

  "extract block" should "get events correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    val extractor = new DefaultEventExtractor()
    val transferExtractor = new TransferEventExtractor()
    extractor.registerBlockExtractor(new BlockGasPriceExtractor)
    extractor.registerTxExtractor(transferExtractor)
    val events = Await.result(extractor.extractEvents(blockData), 5.second)
    info(s"${events}")
  }
}
