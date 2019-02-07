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

import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.relayer.base._
import io.lightcone.core._
import org.web3j.crypto.Credentials
import scala.concurrent.Await

class AllowanceEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport {

  "ethereum event extractor actor test" must {
    "correctly extract Approval events from ethereum blocks" in {
      val getBaMethod = "get_balance_and_allowance"
      val account1 = Credentials.create(
        "0xd90ff3f9d2d9778f27965930480c222a7a49ef3e3a8c64fae78a1d841456847d"
      )
      val ba = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )

      val lrc_ba = ba.balanceAndAllowanceMap(LRC_TOKEN.address)
      val weth_ba = ba.balanceAndAllowanceMap(WETH_TOKEN.address)
      info(
        s"${account1.getAddress} allowance is ${BigInt(lrc_ba.allowance.toByteArray)}"
      )
      Await.result(
        transferEth(account1.getAddress, "1000")(accounts.head),
        timeout.duration
      )
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)
      info(s"${account1.getAddress} approve WETH")
      Await.result(approveWETHToDelegate("1000000")(account1), timeout.duration)
      Thread.sleep(1000)
      val ba2 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      val lrc_ba2 = ba2.balanceAndAllowanceMap(LRC_TOKEN.address)
      val weth_ba2 = ba2.balanceAndAllowanceMap(WETH_TOKEN.address)
      info(
        s"${account1.getAddress} LRC allowance is ${BigInt(lrc_ba2.allowance.toByteArray)}"
      )
      info(
        s"${account1.getAddress} WETH allowance is ${BigInt(weth_ba2.allowance.toByteArray)}"
      )

      (BigInt(lrc_ba2.allowance.toByteArray) - BigInt(
        lrc_ba.allowance.toByteArray
      )).toString() should be("1000000" + "0" * LRC_TOKEN.decimals)

      (BigInt(weth_ba2.allowance.toByteArray) - BigInt(
        weth_ba.allowance.toByteArray
      )).toString() should be("1000000" + "0" * WETH_TOKEN.decimals)
    }
  }
}
