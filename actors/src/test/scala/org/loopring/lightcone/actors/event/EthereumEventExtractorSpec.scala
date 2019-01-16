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

package org.loopring.lightcone.actors.event

import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._

import scala.concurrent.Await

class EthereumEventExtractorSpec
    extends CommonSpec
    with EthereumSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with OrderbookManagerSupport
    with EthereumEventExtractorSupport
  {
    "ethereum event extractor actor test" must {
      "correctly extract all kinds events from ethereum blocks" in {
        val getBaMethod = "get_balance_and_allowance"
        val submit_order = "submit_order"
        val account0 = accounts(0)
        val getOrderBook1 = GetOrderbook.Req(
          0,
          100,
          Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
        )
        val order1 = createRawOrder()(account1)
        val order2 = createRawOrder(
          tokenB = LRC_TOKEN.address,
          tokenS = WETH_TOKEN.address,
          amountB = order1.amountS,
          amountS = order1.amountB
        )(account0)

        val ba1F = singleRequest(GetBalanceAndAllowances.Req(
          accounts(0).getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),getBaMethod).mapAs[GetBalanceAndAllowances.Res]

        val ba1 = Await.result(ba1F,timeout.duration)

        val ba2F = singleRequest(GetBalanceAndAllowances.Req(
          accounts(1).getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),getBaMethod).mapAs[GetBalanceAndAllowances.Res]

        val ba2 = Await.result(ba2F,timeout.duration)


        info("submit the first order.")
        val submitOrder1F =
          singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
            .mapAs[SubmitOrder.Res]
        Await.result(submitOrder1F, timeout.duration)



      }

    }
}
