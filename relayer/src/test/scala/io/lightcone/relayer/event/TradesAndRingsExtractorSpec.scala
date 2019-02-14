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

import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent.Await
import scala.concurrent.duration._

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

      expectBalanceRes(
        GetBalanceAndAllowances.Req(
          account2.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetBalanceAndAllowances.Res) => {
          BigInt(
            res.balanceAndAllowanceMap(WETH_TOKEN.address).allowance.toByteArray
          ) > 0
        },
        timeout
      )
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
      expectTradeRes(
        GetTrades.Req(owner = account1.getAddress),
        (res: GetTrades.Res) => {
          res.trades.length == 1
        }
      )
      info("query trades: by owner")
      val tres1 = Await.result(
        singleRequest(GetTrades.Req(owner = account1.getAddress), "get_trades")
          .mapTo[GetTrades.Res],
        5.second
      )
      tres1.trades.length should be(1)
      val tres2 = Await.result(
        singleRequest(GetTrades.Req(owner = account2.getAddress), "get_trades")
          .mapTo[GetTrades.Res],
        5.second
      )
      tres2.trades.length should be(1)
      val req = GetRings.Req()
      val res = Await.result(
        singleRequest(req, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      res.rings.nonEmpty should be(true)
    }
  }

}
