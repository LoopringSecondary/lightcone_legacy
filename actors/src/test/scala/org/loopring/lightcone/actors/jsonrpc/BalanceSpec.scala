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

package org.loopring.lightcone.actors.jsonrpc

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import org.loopring.lightcone.actors.RpcBinding
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto.XGetBalanceReq
import org.json4s.jackson.Serialization
import org.json4s.DefaultFormats
import org.loopring.lightcone.actors.core.MultiAccountManagerActor
import org.loopring.lightcone.actors.validator.{
  MessageValidationActor,
  MultiAccountManagerMessageValidator
}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class BalanceSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "ethereum_access",
                         | "multi_account_manager",
                         | "ethereum_query",
                         | "gas_price"]
                         |""".stripMargin)
    with EthereumSupport
    with MultiAccountManagerSupport
    with JsonrpcSupport {

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }
  implicit val formats = DefaultFormats

  val host = "localhost"
  val port = 8080
  val relayUri = "/api/loopring"
  val ethUri = "/api/ethereum"

  val owner = "0xb94065482ad64d4c2b9252358d746b39e820a582"

  val getBalanceReq =
    XGetBalanceReq(owner, tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address))

  val rpcReq =
    Map("id" → 1, "method" → "get_balance_and_allowance", "jsonrpc" → "2.0")

  val responseFuture: Future[HttpResponse] = Http().singleRequest(
    HttpRequest(
      method = HttpMethods.POST,
      uri = s"http://localhost:8080$relayUri",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        Serialization.write(rpcReq.+("params" → getBalanceReq))
      )
    )
  )

  val res = Await.result(responseFuture, 2 seconds)

  println(res)

  Thread.sleep(60 * 60 * 1000)
}
