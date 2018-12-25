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

package org.loopring.lightcone.actors.entrypoint

import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._

import scala.concurrent.Await

class EntryPointSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "ring_settlement"]
                         |""".stripMargin)
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  "send an orderbook request" must {
    "receive a response without value" in {
      val getOrderBook = XGetOrderbook(
        0,
        2,
        Some(
          XMarketId(
            LRC_TOKEN.address,
            WETH_TOKEN.address
          )
        )
      )
      val f = singleRequest(
        getOrderBook,
        "orderbook"
      )

      val res = Await.result(f, timeout.duration)
      res match {
        case XOrderbook(lastPrice, sells, buys) =>
          assert(sells.isEmpty)
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}
