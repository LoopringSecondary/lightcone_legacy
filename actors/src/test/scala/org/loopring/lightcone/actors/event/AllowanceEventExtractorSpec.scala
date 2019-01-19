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
import org.loopring.lightcone.actors.base.safefuture._

import scala.concurrent.Await

class AllowanceEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport {

  "ethereum event extractor actor test" must {
    "correctly extract Approval events from ethereum blocks" in {
      val getBaMethod = "get_balance_and_allowance"
      val account1 = accounts(1)
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
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)
      Thread.sleep(1000)
      val ba2_1 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account1.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      val lrc_ba2_1 = ba2_1.balanceAndAllowanceMap(LRC_TOKEN.address)
      info(
        s"${account1.getAddress} allowance is ${BigInt(lrc_ba2_1.allowance.toByteArray)}"
      )
      (BigInt(lrc_ba2_1.allowance.toByteArray) - BigInt(
        lrc_ba2.allowance.toByteArray
      )).toString() should be("1000000" + "0" * LRC_TOKEN.decimals)
    }
  }
}
