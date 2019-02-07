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

package io.lightcone.relayer.entrypoint

import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.Await

class EntryPointSpec
    extends CommonSpec
    with EthereumSupport
    with MetadataManagerSupport
    with OrderbookManagerSupport
    with MarketManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  "send an orderbook request" must {
    "receive a response without value" in {
      val getOrderBook = GetOrderbook.Req(
        0,
        2,
        Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val f = singleRequest(getOrderBook, "get_orderbook")

      val res = Await.result(f, timeout.duration)
      res match {
        case GetOrderbook.Res(Some(Orderbook(lastPrice, sells, buys))) =>
          assert(sells.isEmpty)
          assert(buys.isEmpty)
        case _ => assert(false)
      }
    }
  }

}