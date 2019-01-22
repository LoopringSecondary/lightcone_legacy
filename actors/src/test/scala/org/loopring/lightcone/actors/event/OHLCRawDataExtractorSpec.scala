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
import org.web3j.crypto.Credentials
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.lib.MarketHashProvider._

import scala.concurrent.Await

class OHLCRawDataExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {

  "ethereum event extractor actor test" must {
    "correctly extract all OHLC raw data from ethereum blocks" in {

      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val account0 = accounts.head
      val account1 = Credentials.create(
        "0x1a9a50f04f21ed5c0f1ed8da1d44c238de3184b99f38108f475c7e1959fb0fb6"
      )
      val account2 = Credentials.create(
        "0x649b8fc4b2a8cd2d222977878e3dc95565df8e794e7fcc2fabae0a70c935ee07"
      )

      Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      Await.result(
        transferEth(account1.getAddress, "100")(account0),
        timeout.duration
      )
      Await.result(
        transferEth(account2.getAddress, "100")(account0),
        timeout.duration
      )
      info("transfer to account1 1000 LRC and approve")
      Await.result(
        transferLRC(account1.getAddress, "1000")(account0),
        timeout.duration
      )
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)

      info("transfer to account2 1000 WETH and approve")
      Await.result(
        transferWETH(account2.getAddress, "1000")(account0),
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
      Thread.sleep(100)
      val order3 = createRawOrder()(account1)
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order3)), submit_order)
          .mapAs[SubmitOrder.Res],
        timeout.duration
      )
      Thread.sleep(6000)
      val marketKey = convert2Hex(LRC_TOKEN.address, WETH_TOKEN.address)
      val ohlcDatas = Await.result(
        dbModule.ohlcDataDal.getOHLCData(
          marketKey,
          60,
          timeProvider.getTimeSeconds() - 600,
          timeProvider.getTimeSeconds()
        ),
        timeout.duration
      )
      ohlcDatas.nonEmpty should be(true)
    }
  }
}
