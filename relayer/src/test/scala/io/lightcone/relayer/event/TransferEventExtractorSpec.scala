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

import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent.Await

class TransferEventExtractorSpec extends CommonSpec with EventExtractorSupport {

  "extract  transfer events  " must {
    "extract all transfer events and activities" in {
      val getAccountMethod = "get_account"
      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth
      val ba1 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(
              Address.ZERO.toString(),
              LRC_TOKEN.address,
              WETH_TOKEN.address
            )
          ),
          getAccountMethod
        ).mapAs[GetAccount.Res].map(_.getAccountBalance.tokenBalanceMap),
        timeout.duration
      )

      info(s"transfer to ${account1.getAddress} 50 ETH")
      Await.result(
        transferEth(account1.getAddress, "50")(account0),
        timeout.duration
      )
      info(s"transfer to ${account1.getAddress} 50 WETH")
      Await.result(
        transferWETH(account1.getAddress, "50")(account0),
        timeout.duration
      )
      info(s"transfer to ${account1.getAddress} 1000 LRC")
      Await.result(
        transferLRC(account1.getAddress, "1000")(account0),
        timeout.duration
      )
      expectBalanceRes(
        GetAccount.Req(
          account1.getAddress,
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        (res: GetAccount.Res) => {
          val lrcBalance: BigInt =
            res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address).balance
          lrcBalance == "1000".zeros(LRC_TOKEN.decimals)
        }
      )

      val ba2 = Await.result(
        singleRequest(
          GetAccount.Req(
            account1.getAddress,
            tokens = Seq(
              Address.ZERO.toString(),
              LRC_TOKEN.address,
              WETH_TOKEN.address
            )
          ),
          getAccountMethod
        ).mapAs[GetAccount.Res].map(_.getAccountBalance.tokenBalanceMap),
        timeout.duration
      )

      (NumericConversion.toBigInt(ba2(LRC_TOKEN.address).getBalance) - NumericConversion
        .toBigInt(
          ba1(LRC_TOKEN.address).getBalance
        )).toString() should be("1000" + "0" * LRC_TOKEN.decimals)

      (NumericConversion.toBigInt(ba2(Address.ZERO.toString()).getBalance) - NumericConversion
        .toBigInt(
          ba1(Address.ZERO.toString()).getBalance
        )).toString() should be("50" + "0" * WETH_TOKEN.decimals)

      (NumericConversion.toBigInt(ba2(WETH_TOKEN.address).getBalance) - NumericConversion
        .toBigInt(
          ba1(WETH_TOKEN.address).getBalance
        )).toString() should be("50" + "0" * WETH_TOKEN.decimals)

    }

  }

}
