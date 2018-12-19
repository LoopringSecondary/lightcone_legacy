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

package org.loopring.lightcone.actors.core

import akka.actor.Props
import org.loopring.lightcone.actors.RpcBinding
import org.loopring.lightcone.actors.entrypoint.EntryPointActor
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer
import org.loopring.lightcone.actors.support.{CommonSpec, DatabaseModuleSupport}
import org.loopring.lightcone.actors.validator.{
  DatabaseQueryMessageValidator,
  MessageValidationActor
}
import scala.concurrent.{Await, Future}
import akka.http.scaladsl.model._
import akka.util.ByteString
import scala.concurrent.duration._
import scala.concurrent.Future
import akka.http.scaladsl.Http
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto.{MarketPair, XGetTradesReq, XSkip, XSort}
import scala.util.{Failure, Success}
import org.json4s._
import org.json4s.jackson.Serialization.write

class DatabaseQuerySpec
    extends CommonSpec("""
                         |akka.cluster.roles=["database_query"]
                         |""".stripMargin)
    with DatabaseModuleSupport {
  actors.add(
    DatabaseQueryMessageValidator.name,
    MessageValidationActor(
      new DatabaseQueryMessageValidator(),
      DatabaseQueryActor.name,
      DatabaseQueryMessageValidator.name
    )
  )
  actors.add(
    EntryPointActor.name,
    system.actorOf(Props(new EntryPointActor()), EntryPointActor.name)
  )

  Future {
    val server = new JsonRpcServer(config, actors.get(EntryPointActor.name))
    with RpcBinding
    server.start()
  }

  "getOrdersWithJRPC" should {
    "query owner's orders successfully" in {
      val jsonByteString = ByteString(
        s"""{
           |  "method":"get_orders",
           |  "params" : {"owner":"0x1","statuses":["STATUS_NEW"]},
           |  "id" : 0,
           |  "jsonrpc":"000"
           |}""".stripMargin
      )
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "http://127.0.0.1:8080/api/loopring",
        entity = HttpEntity(MediaTypes.`application/json`, jsonByteString)
      )
      val response = Http().singleRequest(request)
      val result = Await.result(response.mapTo[HttpResponse], 10 second)
      result.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .foreach(body => println(body.utf8String))
      result.status === StatusCodes.OK should be(true)
    }
  }

  "getTradesWithJRPC" should {
    "query owner's trades successfully" in {
      val a = XGetTradesReq(
        owner = "0x-gettrades-state0-02",
        market = XGetTradesReq.Market
          .MarketHash(MarketHashProvider.convert2Hex("0x00001", "0x00002")),
        skip = Some(XSkip(0, 10)),
        sort = XSort.ASC
      )
      val b = XGetTradesReq(
        owner = "0x-gettrades-token-02",
        market = XGetTradesReq.Market
          .Pair(MarketPair(tokenB = "0x00001", tokenS = "0x00002")),
        skip = Some(XSkip(0, 10)),
        sort = XSort.ASC
      )
      implicit val formats = DefaultFormats
      // TODO du: write(a)无法序列化enum，暂时写死参数
      // {"owner":"0x-gettrades-state0-02","market":{"value":"0x3"}}
      // {"owner":"0x-gettrades-token-02","skip":{"skip":0,"take":10},"market":{"value":{"tokenS":"0x00002","tokenB":"0x00001"}}}
      val jsonByteString = ByteString(
        s"""{
           |  "method":"get_trades",
           |  "params" : {
           |    "owner":"0x-gettrades-token-02",
           |    "skip":{"skip":0,"take":10},
           |    "market":{"value":{"tokenS":"0x00002","tokenB":"0x00001"}}
           |   },
           |  "id" : 0,
           |  "jsonrpc":"000"
           |}""".stripMargin
      )
      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = "http://127.0.0.1:8080/api/loopring",
        entity = HttpEntity(MediaTypes.`application/json`, jsonByteString)
      )
      val response = Http().singleRequest(request)
      response onComplete {
        case Success(value) => println(value)
        case Failure(e)     => println(e)
      }
      val result = Await.result(response.mapTo[HttpResponse], 10 second)
      val body = result.entity.dataBytes
        .runFold(ByteString(""))(_ ++ _)
        .map(_.utf8String)
      result.status === StatusCodes.OK should be(true)
    }
  }

}
