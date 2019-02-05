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

package io.lightcone.actors.event

import io.lightcone.actors.base.safefuture._
import io.lightcone.actors.support._
import io.lightcone.proto._
import io.lightcone.core._
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import io.lightcone.actors.data._
import scala.concurrent.Await

class RingMinedAndOrderFilledEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {
  "ethereum event extractor actor test" must {
    "correctly extract all kinds events from ethereum blocks" in {
      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val account0 = accounts.head
      val account1 = Credentials.create(
        "0xdd2c4a5c56cb9b02f52d6c4bc56da9219290c3adbe76efc082dbfb98542a9641"
      )
      val account2 = Credentials.create(
        "0x2cc04482d2fba83ff646d0aeacc7f5195fa2532859fdec917354ba4691e8444a"
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
      val ba1_1 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      val ba2_1 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )

      val lrc_ba1_1 = ba1_1.balanceAndAllowanceMap(LRC_TOKEN.address)
      val lrc_ba2_1 = ba2_1.balanceAndAllowanceMap(LRC_TOKEN.address)
      val weth_ba1_1 = ba1_1.balanceAndAllowanceMap(WETH_TOKEN.address)
      val weth_ba2_1 = ba2_1.balanceAndAllowanceMap(WETH_TOKEN.address)

      info(
        "submit two orders and wait for ring submitter,extract ringMined event"
      )
      val order1 = createRawOrder()(account1)
      val order2 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = BigInt(order1.amountS.toByteArray),
        amountS = BigInt(order1.amountB.toByteArray)
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
      Thread.sleep(10000)

      val ba1_2 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )

      val ba2_2 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      val lrc_ba1_2 = ba1_2.balanceAndAllowanceMap(LRC_TOKEN.address)
      val lrc_ba2_2 = ba2_2.balanceAndAllowanceMap(LRC_TOKEN.address)
      val weth_ba1_2 = ba1_2.balanceAndAllowanceMap(WETH_TOKEN.address)
      val weth_ba2_2 = ba2_2.balanceAndAllowanceMap(WETH_TOKEN.address)

      (BigInt(weth_ba1_2.balance.toByteArray) - BigInt(
        weth_ba1_1.balance.toByteArray
      )).toString() should be("1" + "0" * WETH_TOKEN.decimals)

      (BigInt(lrc_ba1_1.balance.toByteArray) - BigInt(
        lrc_ba1_2.balance.toByteArray
      )).toString() should be("13" + "0" * LRC_TOKEN.decimals)

      (BigInt(lrc_ba1_1.allowance.toByteArray) - BigInt(
        lrc_ba1_2.allowance.toByteArray
      )).toString() should be("13" + "0" * LRC_TOKEN.decimals)

      (BigInt(lrc_ba2_2.balance.toByteArray) - BigInt(
        lrc_ba2_1.balance.toByteArray
      )).toString() should be("7" + "0" * LRC_TOKEN.decimals)

      (BigInt(weth_ba2_1.balance.toByteArray) - BigInt(
        weth_ba2_2.balance.toByteArray
      )).toString() should be("1" + "0" * WETH_TOKEN.decimals)

      //      val getOrder1 = Await.result(
      //        dbModule.orderService.getOrder(order1.hash),
      //        timeout.duration
      //      )
      //      println(
      //        byteString2BigInt(getOrder1.get.getState.outstandingAmountS)
      //      )
      //      println(getOrder1.get.getState.status)
      //      val getOrder2 = Await.result(
      //        dbModule.orderService.getOrder(order2.hash),
      //        timeout.duration
      //      )
      //      println(
      //        byteString2BigInt(getOrder2.get.getState.outstandingAmountS)
      //      )
      //      println(getOrder2.get.getState.status)

    }
  }
}
