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
import org.loopring.lightcone.proto._
import org.json4s.jackson.Serialization
import org.json4s.DefaultFormats
import org.loopring.lightcone.actors.core.MultiAccountManagerActor
import org.loopring.lightcone.actors.validator._
import akka.pattern._
import com.google.protobuf.ByteString
import org.web3j.utils.Numeric

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

  for {
    ba1 ← Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8080$relayUri",
          entity = HttpEntity(
            ContentTypes.`application/json`,
            Serialization.write(rpcReq.+("params" → getBalanceReq))
          )
        )
      )
      .flatMap(res ⇒ res.entity.dataBytes.map(_.utf8String).runReduce(_ + _))
      .map { Serialization.read[JsonRpcResponse] }
//      .map { res ⇒
//        val ba = res.result.get.extract[XGetBalanceAndAllowancesRes]
    ////        ba.balanceAndAllowanceMap.map(
    ////          item ⇒
    ////            (
    ////              BigInt(item._2.balance.toByteArray),
    ////              BigInt(item._2.allowance.toByteArray),
    ////              BigInt(item._2.availableBalance.toByteArray),
    ////              BigInt(item._2.availableAllowance.toByteArray)
    ////            )
    ////        )
    ////      }
//    orderRes ← (actors.get(MultiAccountManagerMessageValidator.name) ? XSubmitSimpleOrderReq()
//      .withOwner(owner)
//      .withOrder(
//        XOrder()
//          .withTokenS(LRC_TOKEN.address)
    ////          .withTokenB((WETH_TOKEN.address))
    ////          .withAmountS(
    ////            ByteString
    ////              .copyFrom(Numeric.hexStringToByteArray("0x54607fc96a60000"))
    ////          )
    ////          .withAmountS(
    ////            ByteString
    ////              .copyFrom(Numeric.hexStringToByteArray("0x54607fc96a60000"))
    ////          )
//      )).mapTo[XSubmitOrderRes]

    ba2 ← Http()
      .singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:8080$relayUri",
          entity = HttpEntity(
            ContentTypes.`application/json`,
            Serialization.write(rpcReq.+("params" → getBalanceReq))
          )
        )
      )
      .flatMap(res ⇒ res.entity.dataBytes.map(_.utf8String).runReduce(_ + _))
      .map { Serialization.read[JsonRpcResponse] }
//      .map { res ⇒
//        val ba = res.extract[XGetBalanceAndAllowancesRes]
//        ba.balanceAndAllowanceMap.map(
//          item ⇒
//            (
//              BigInt(item._2.balance.toByteArray),
//              BigInt(item._2.allowance.toByteArray),
//              BigInt(item._2.availableBalance.toByteArray),
//              BigInt(item._2.availableAllowance.toByteArray)
//            )
//        )
//      }

  } yield {

    println(s"ba1：$ba1")
    println(s"ba2: $ba2")
//    println(orderRes)
  }

  Thread.sleep(60 * 60 * 1000)
}
