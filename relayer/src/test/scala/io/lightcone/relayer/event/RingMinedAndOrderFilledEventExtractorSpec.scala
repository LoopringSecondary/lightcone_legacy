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

import akka.util.Timeout
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._
import scala.concurrent.duration._

import scala.concurrent.Await

class RingMinedAndOrderFilledEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {
  "ethereum event extractor actor test" must {
    "correctly extract all kinds events from ethereum blocks" in {
      val getBaMethod = "get_account"
      val submit_order = "submit_order"
      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth
      val account2 = getUniqueAccountWithoutEth
      Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )
      Await.result(
        singleRequest(
          GetAccount.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
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

      expectBalanceRes(
        GetAccount.Req(
          account2.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          BigInt(
            res.getAccountBalance
              .tokenBalanceMap(WETH_TOKEN.address)
              .balance
              .toByteArray
          ) > 0
        }
      )

      val ba1_1 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )
      val ba2_1 = Await.result(
        singleRequest(
          GetAccount.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )

      val lrc_ba1_1 = ba1_1.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val lrc_ba2_1 = ba2_1.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val weth_ba1_1 =
        ba1_1.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)
      val weth_ba2_1 =
        ba2_1.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)

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
      expectBalanceRes(
        GetAccount.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          BigInt(
            res.getAccountBalance
              .tokenBalanceMap(LRC_TOKEN.address)
              .balance
              .toByteArray
          ) < "1000".zeros(LRC_TOKEN.decimals)
        },
        Timeout(10 seconds)
      )
      val ba1_2 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )

      val ba2_2 = Await.result(
        singleRequest(
          GetAccount.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )
      val lrc_ba1_2 = ba1_2.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val lrc_ba2_2 = ba2_2.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val weth_ba1_2 =
        ba1_2.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)
      val weth_ba2_2 =
        ba2_2.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)

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
