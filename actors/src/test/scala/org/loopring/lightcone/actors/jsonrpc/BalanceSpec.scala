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

import akka.pattern._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.lib.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.proto._

import scala.concurrent.Await

class BalanceSpec
    extends CommonSpec
    with EthereumSupport
    with MetadataManagerSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderHandleSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with OrderGenerateSupport
    with HttpSupport {

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "send an query balance request" must {
    "receive a response with balance" in {
      val method = "get_balance_and_allowance"
      val getBalanceReq =
        GetBalanceAndAllowances.Req(
          accounts(0).getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        )
      val maker = createRawOrder(
        amountS = "1".zeros(18),
        amountB = "100".zeros(10),
        amountFee = "1".zeros(16)
      )
      val r = for {
        firstQuery <- singleRequest(getBalanceReq, method)
        _ <- (actors.get(MultiAccountManagerMessageValidator.name) ? SubmitOrder
          .Req(Some(maker))).mapTo[SubmitOrder.Res]
        secondQuery <- singleRequest(getBalanceReq, method)
      } yield (firstQuery, secondQuery)
      val res = Await.result(r, timeout.duration)
      res match {
        case (f: GetBalanceAndAllowances.Res, s: GetBalanceAndAllowances.Res) =>
          val bf: BigInt =
            f.balanceAndAllowanceMap(LRC_TOKEN.address).availableBalance
          val bs: BigInt =
            s.balanceAndAllowanceMap(LRC_TOKEN.address).availableBalance
          val sold: BigInt = maker.amountS
          val fee: BigInt = maker.amountFee
          info(s"matcheAmount: ${bf - bs}, expectAmount ${sold + fee}")
          assert(bf - bs >= sold + fee - 1000 && bf - bs <= sold + fee) //有计算的误差
        case _ => assert(false)
      }
    }
  }
}
