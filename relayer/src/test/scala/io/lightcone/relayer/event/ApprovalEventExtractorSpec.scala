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

import io.lightcone.lib.NumericConversion
import io.lightcone.relayer.base._
import io.lightcone.relayer.data.GetAccount
import io.lightcone.relayer.support._

import scala.concurrent.Await

class ApprovalEventExtractorSpec extends CommonSpec with EventExtractorSupport {

  "extract  approval events  " must {
    "extract all approval events and activities" in {
      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth
      val getAccountMethod = "get_account"
      val ba1 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(
              LRC_TOKEN.address,
              WETH_TOKEN.address
            )
          ),
          getAccountMethod
        ).mapAs[GetAccount.Res].map(_.getAccountBalance.tokenBalanceMap),
        timeout.duration
      )

      info(s"transfer 20 eth to ${account1.getAddress}")

      Await.result(
        transferEth(account1.getAddress, "20")(account0),
        timeout.duration
      )

      info(s"${account1.getAddress} approve 10000 LRC ")

      Await.result(
        approveLRCToDelegate("10000")(account1),
        timeout.duration
      )

      expectBalanceRes(
        GetAccount.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          val lrcBalance: BigInt =
            res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address).allowance
          lrcBalance == "10000".zeros(LRC_TOKEN.decimals)
        }
      )

      val ba2 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(
              LRC_TOKEN.address,
              WETH_TOKEN.address
            )
          ),
          getAccountMethod
        ).mapAs[GetAccount.Res].map(_.getAccountBalance.tokenBalanceMap),
        timeout.duration
      )

      (NumericConversion.toBigInt(ba2(LRC_TOKEN.address).getAllowance) - NumericConversion
        .toBigInt(
          ba1(LRC_TOKEN.address).getAllowance
        )).toString() should be("10000" + "0" * LRC_TOKEN.decimals)

      info(s"${account1.getAddress} approve 0 LRC ")

      Await.result(
        approveLRCToDelegate("0")(account1),
        timeout.duration
      )

      expectBalanceRes(
        GetAccount.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          val lrcAllowance: BigInt =
            res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address).allowance
          lrcAllowance == "0".zeros(LRC_TOKEN.decimals)
        }
      )

      val ba3 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(
              LRC_TOKEN.address,
              WETH_TOKEN.address
            )
          ),
          getAccountMethod
        ).mapAs[GetAccount.Res].map(_.getAccountBalance.tokenBalanceMap),
        timeout.duration
      )

      NumericConversion.toBigInt(ba3(LRC_TOKEN.address).getAllowance) should be(
        BigInt(0)
      )

    }
  }

}
