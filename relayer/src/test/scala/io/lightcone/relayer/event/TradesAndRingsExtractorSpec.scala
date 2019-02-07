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

package io.lightcone.relayer.event

import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.relayer.base._
import scala.concurrent.duration._
import scala.concurrent.Await

class TradesAndRingsExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {

  "ethereum event extractor actor test" must {
    "correctly extract all trades and rings from ethereum blocks" in {

      val submit_order = "submit_order"
      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth
      val account2 = getUniqueAccountWithoutEth

      Await.result(
        transferEth(account1.getAddress, "10")(account0),
        timeout.duration
      )
      Await.result(
        transferEth(account2.getAddress, "10")(account0),
        timeout.duration
      )
      info("transfer to account1 1000 LRC and approve")
      Await.result(
        transferLRC(account1.getAddress, "100")(account0),
        timeout.duration
      )
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)

      info("transfer to account2 1000 WETH and approve")
      Await.result(
        transferWETH(account2.getAddress, "100")(account0),
        timeout.duration
      )
      info(s"${account2.getAddress} approve WETH")
      Await.result(approveWETHToDelegate("1000000")(account2), timeout.duration)

      Thread.sleep(1000)
      val order1 = createRawOrder()(account1)
      val order2 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = 2 * BigInt(order1.amountS.toByteArray),
        amountS = 2 * BigInt(order1.amountB.toByteArray)
      )(account2)

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order2)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )

      Thread.sleep(2000)

      info("query trades: by owner")
      val treq1 = GetTrades.Req(owner = account1.getAddress)
      val tres1 = Await.result(
        singleRequest(treq1, "get_trades").mapTo[GetTrades.Res],
        5.second
      )

      assert(tres1.trades.length == 1)
      println(tres1.trades.head)

      val treq2 = GetTrades.Req(owner = account2.getAddress)
      val tres2 = Await.result(
        singleRequest(treq2, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(tres2.trades.length == 1)
      println(tres2.trades.head)
      val req = GetRings.Req()
      val res = Await.result(
        singleRequest(req, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(res.rings.nonEmpty)
    }
  }

}
