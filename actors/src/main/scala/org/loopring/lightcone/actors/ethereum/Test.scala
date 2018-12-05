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

package org.loopring.lightcone.actors.ethereum

import org.json4s.native.JsonMethods.parse
import org.json4s._
import org.json4s.jackson.Serialization
import org.loopring.lightcone.proto.actors._
import scalapb.json4s.JsonFormat
object Test extends App {

  val res = "[{\"jsonrpc\":\"2.0\",\"id\":83,\"result\":{\"blockHash\":\"0xdb2a95cf9c931e516847d866a85edebd75c4b78b82cf1411e7f6e47f45253018\",\"blockNumber\":\"0x6806a4\",\"contractAddress\":null,\"cumulativeGasUsed\":\"0x2caa8b\",\"from\":\"0x33debb5ee65549ffa71116957da6db17a9d8fe57\",\"gasUsed\":\"0x6eba\",\"logs\":[{\"address\":\"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2\",\"topics\":[\"0xe1fffcc4923d04b559f4d29a8bfc6cda04eb5b0d3c460751c2402c5c5cc9109c\",\"0x00000000000000000000000033debb5ee65549ffa71116957da6db17a9d8fe57\"],\"data\":\"0x0000000000000000000000000000000000000000000000000de0b6b3a7640000\",\"blockNumber\":\"0x6806a4\",\"transactionHash\":\"0x80a8e3b75c5c5a863ec305d0aa0556012e51f2553ce904e53929ce572aa37fac\",\"transactionIndex\":\"0x29\",\"blockHash\":\"0xdb2a95cf9c931e516847d866a85edebd75c4b78b82cf1411e7f6e47f45253018\",\"logIndex\":\"0x1e\",\"removed\":false}],\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000020000000000000000020000002000000080000000000000000000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000800000000400000000000000000\",\"status\":\"0x1\",\"to\":\"0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2\",\"transactionHash\":\"0x80a8e3b75c5c5a863ec305d0aa0556012e51f2553ce904e53929ce572aa37fac\",\"transactionIndex\":\"0x29\"}},{\"jsonrpc\":\"2.0\",\"id\":83,\"result\":null}]"
  val t = parse(res)

  implicit val format = DefaultFormats
  //val jsonstrs: List[Map[String, Any]] = t.values.asInstanceOf[List[Map[String,Any]]]
  //
  //
  // val receiptResps =  jsonstrs.map(str ⇒ {
  //    val sigleRes =  Serialization.write(str)
  //   JsonFormat.fromJsonString[XGetTransactionReceiptRes](sigleRes)
  //  })
  //
  //  println(XBatchGetTransactionReceiptsRes(receiptResps))


  val rpcRes = parse("{\"jsonrpc\":\"2.0\",\"id\":83,\"result\":null}").extract[JsonRpcResWrapped]

//  println(rpcRes)
//  println(rpcRes.result == null)
//

println(JsonFormat.fromJsonString[XGetTransactionReceiptRes]("{\"jsonrpc\":\"2.0\",\"id\":83,\"result\":null}"))

}
