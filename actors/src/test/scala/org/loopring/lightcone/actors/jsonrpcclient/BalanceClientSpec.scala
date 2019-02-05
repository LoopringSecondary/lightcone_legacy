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

//package org.loopring.lightcone.actors.jsonrpcclient
//
//import akka.actor.ActorSystem
//import akka.pattern._
//import akka.stream.ActorMaterializer
//import akka.util.Timeout
//import com.typesafe.config.{Config, ConfigFactory}
//import org.loopring.lightcone.actors.data._
//import org.loopring.lightcone.actors.support._
//import org.loopring.lightcone.actors.validator._
//import org.loopring.lightcone.proto._
//import org.scalatest.WordSpec
//import scala.concurrent.duration._
//import scala.concurrent.{Await, ExecutionContext}
//
//class BalanceClientSpec extends WordSpec with HttpSupport {
//
//  "send an query balance request" must {
//    "receive a response with balance" in {
//      val method = "get_balance_and_allowance"
//      val getBalanceReq =
//        GetBalanceAndAllowances.Req(
//          accounts(0).getAddress,
//          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
//        )
//      val f = singleRequest(getBalanceReq, method)
//
//      val res = Await.result(f, timeout.duration)
//
//      info(s"${res}")
//
//    }
//  }
//  override implicit val system: ActorSystem = ActorSystem()
//  override val config: Config = system.settings.config
//  override implicit val materializer: ActorMaterializer = ActorMaterializer()
//  override implicit val timeout: Timeout = Timeout(5 second)
//  override implicit val ec: ExecutionContext = system.dispatcher
//}
