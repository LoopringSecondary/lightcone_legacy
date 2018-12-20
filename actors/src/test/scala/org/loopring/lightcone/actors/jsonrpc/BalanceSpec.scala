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

import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.validator._
import akka.pattern._
import scala.concurrent.{Await, Future}

class BalanceSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "ethereum_access",
                         | "multi_account_manager",
                         | "ethereum_query",
                         | "gas_price",
                         | "orderbook_manager",
                         | "ring_settlement",
                         | "market_manager"]
                         |""".stripMargin)
    with EthereumSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "send an query balance request" must {
    "receive a response with balance" in {
      val method = "get_balance_and_allowance"
      val owner = "0xb94065482ad64d4c2b9252358d746b39e820a582"
      val getBalanceReq =
        XGetBalanceAndAllowancesReq(
          owner,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        )
      val maker = XOrder(
        id = "maker1",
        tokenS = LRC_TOKEN.address,
        tokenB = WETH_TOKEN.address,
        tokenFee = LRC_TOKEN.address,
        amountS = "1".zeros(18),
        amountB = "100".zeros(10),
        amountFee = "1".zeros(16),
        walletSplitPercentage = 0.2,
        status = XOrderStatus.STATUS_NEW,
        reserved =
          Some(XOrderState("1".zeros(18), "100".zeros(10), "1".zeros(16))),
        outstanding =
          Some(XOrderState("1".zeros(18), "100".zeros(10), "1".zeros(16))),
        actual =
          Some(XOrderState("1".zeros(18), "100".zeros(10), "1".zeros(16))),
        matchable =
          Some(XOrderState("1".zeros(18), "100".zeros(10), "1".zeros(16)))
      )
      val r = for {
        first <- singleRequest(
          getBalanceReq,
          method
        )
        _ ← (actors.get(MultiAccountManagerMessageValidator.name) ? XSubmitSimpleOrderReq(
          owner = owner,
          order = Some(maker)
        )).mapTo[XSubmitOrderRes]
        second <- singleRequest(
          getBalanceReq,
          method
        )
      } yield (first, second)
      val res = Await.result(r, timeout.duration)
      res match {
        case (f: XGetBalanceAndAllowancesRes, s: XGetBalanceAndAllowancesRes) =>
          val bf: BigInt =
            f.balanceAndAllowanceMap(LRC_TOKEN.address).availableBalance
          val bs: BigInt =
            s.balanceAndAllowanceMap(LRC_TOKEN.address).availableBalance
          val sold: BigInt = maker.amountS
          val fee: BigInt = maker.amountFee
          assert(bf - bs === sold + fee)
        case _ => assert(false)
      }
    }
  }
}
