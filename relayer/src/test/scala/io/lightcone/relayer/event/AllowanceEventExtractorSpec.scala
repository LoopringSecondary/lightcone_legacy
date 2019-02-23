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

class AllowanceEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport {

  "ethereum event extractor actor test" must {
    "correctly extract Approval events from ethereum blocks" in {
      val getBaMethod = "get_account"
      val account1 = getUniqueAccountWithoutEth
      val ba = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )
      val lrc_ba = ba.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val weth_ba = ba.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)
      Await.result(
        transferEth(account1.getAddress, "1000")(accounts.head),
        timeout.duration
      )
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)
      info(s"${account1.getAddress} approve WETH")
      Await.result(approveWETHToDelegate("1000000")(account1), timeout.duration)
      expectBalanceRes(
        GetAccount.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          BigInt(
            res.getAccountBalance
              .tokenBalanceMap(WETH_TOKEN.address)
              .allowance
              .toByteArray
          ) > 0
        }
      )
      val ba2 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetAccount.Res],
        timeout.duration
      )
      val lrc_ba2 = ba2.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
      val weth_ba2 = ba2.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)

      (BigInt(lrc_ba2.allowance.toByteArray) - BigInt(
        lrc_ba.allowance.toByteArray
      )).toString() should be("1000000" + "0" * LRC_TOKEN.decimals)

      (BigInt(weth_ba2.allowance.toByteArray) - BigInt(
        weth_ba.allowance.toByteArray
      )).toString() should be("1000000" + "0" * WETH_TOKEN.decimals)
    }
  }
}
