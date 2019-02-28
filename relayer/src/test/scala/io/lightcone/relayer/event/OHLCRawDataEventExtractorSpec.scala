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

import akka.pattern._
import io.lightcone.core._
import io.lightcone.relayer.actors.MarketHistoryActor
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent.Await

class OHLCRawDataEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {

  "ethereum event extractor actor test" must {
    "correctly extract all OHLC raw data from ethereum blocks" in {

      //TODO(hongyu):OHLCRawData已经更改到persistence，完成之后再打开测试
//      def oHLCDataHandlerActor = actors.get(MarketHistoryActor.name)
//
//      val getBaMethod = "get_account"
//      val submit_order = "submit_order"
//      val account0 = accounts.head
//      val account1 = getUniqueAccountWithoutEth
//      val account2 = getUniqueAccountWithoutEth
//      val account3 = getUniqueAccountWithoutEth
//      Await.result(
//        singleRequest(
//          GetAccount.Req(
//            account1.getAddress,
//            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
//          ),
//          getBaMethod
//        ).mapAs[GetAccount.Res],
//        timeout.duration
//      )
//      Await.result(
//        singleRequest(
//          GetAccount.Req(
//            account2.getAddress,
//            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
//          ),
//          getBaMethod
//        ).mapAs[GetAccount.Res],
//        timeout.duration
//      )
//      Await.result(
//        singleRequest(
//          GetAccount.Req(
//            account3.getAddress,
//            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
//          ),
//          getBaMethod
//        ).mapAs[GetAccount.Res],
//        timeout.duration
//      )
//      Await.result(
//        transferEth(account1.getAddress, "10")(account0),
//        timeout.duration
//      )
//      Await.result(
//        transferEth(account2.getAddress, "10")(account0),
//        timeout.duration
//      )
//      Await.result(
//        transferEth(account3.getAddress, "10")(account0),
//        timeout.duration
//      )
//      info("transfer to account1 1000 LRC and approve")
//      Await.result(
//        transferLRC(account1.getAddress, "1000")(account0),
//        timeout.duration
//      )
//      info(s"${account1.getAddress} approve LRC")
//      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)
//
//      info("transfer to account2 1000 WETH and approve")
//      Await.result(
//        transferWETH(account2.getAddress, "1000")(account0),
//        timeout.duration
//      )
//      info(s"${account2.getAddress} approve WETH")
//      Await.result(approveWETHToDelegate("1000000")(account2), timeout.duration)
//
//      info("transfer to account3 1000 LRC and approve")
//      Await.result(
//        transferLRC(account3.getAddress, "1000")(account0),
//        timeout.duration
//      )
//      info(s"${account3.getAddress} approve LRC")
//      Await.result(approveLRCToDelegate("1000000")(account3), timeout.duration)
//
//      expectBalanceRes(
//        GetAccount.Req(
//          account3.getAddress,
//          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
//        ),
//        (res: GetAccount.Res) => {
//          BigInt(
//            res.getAccountBalance
//              .tokenBalanceMap(LRC_TOKEN.address)
//              .allowance
//              .toByteArray
//          ) > 0
//        }
//      )
//      val order1 = createRawOrder()(account1)
//      val order2 = createRawOrder(
//        tokenB = LRC_TOKEN.address,
//        tokenS = WETH_TOKEN.address,
//        amountB = 2 * BigInt(order1.amountS.toByteArray),
//        amountS = 2 * BigInt(order1.amountB.toByteArray)
//      )(account2)
//      val order3 = createRawOrder()(account3)
//      Await.result(
//        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
//          .mapAs[SubmitOrder.Res],
//        timeout.duration
//      )
//      Await.result(
//        singleRequest(SubmitOrder.Req(Some(order2)), submit_order)
//          .mapAs[SubmitOrder.Res],
//        timeout.duration
//      )
//      Await.result(
//        singleRequest(SubmitOrder.Req(Some(order3)), submit_order)
//          .mapAs[SubmitOrder.Res],
//        timeout.duration
//      )
//      expectTradeRes(
//        GetFills.Req(owner = account3.getAddress),
//        (res: GetFills.Res) => {
//          res.fills.length == 1
//        }
//      )
//      val marketHash =
//        MarketHash(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)).toString
//      val res = Await.result(
//        (oHLCDataHandlerActor ? GetMarketHistory.Req(
//          marketHash,
//          GetMarketHistory.Interval.OHLC_INTERVAL_ONE_MINUTES,
//          timeProvider.getTimeSeconds() - 600,
//          timeProvider.getTimeSeconds()
//        )).mapAs[GetMarketHistory.Res],
//        timeout.duration
//      )
//      res.data.nonEmpty should be(true)
    }
  }
}
