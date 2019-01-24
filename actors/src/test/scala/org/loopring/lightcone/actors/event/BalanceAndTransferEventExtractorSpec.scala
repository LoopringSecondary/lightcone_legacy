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

import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import org.web3j.crypto.Credentials

import scala.concurrent.Await

class BalanceAndTransferEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport {

  "ethereum balance update event and transfer event extractor actor test" must {
    "correctly extract balance update events and transfer events from ethereum blocks" in {
      val getBaMethod = "get_balance_and_allowance"
      val account0 = accounts.head
      val account2 = getUniqueAccountWithoutEth

      val ba2 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account2.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )
      val lrc_ba2 = ba2.balanceAndAllowanceMap(LRC_TOKEN.address)
      info(
        s"transfer to account2:${account2.getAddress}, account0:${accounts(0).getAddress} 1000 LRC, current balance : ${BigInt(lrc_ba2.balance.toByteArray)}"
      )
      val tx = Await.result(
        transferLRC(account2.getAddress, "1000")(account0),
        timeout.duration
      )
      info(s"# BalanceAndTransferEventExtractorSpec transferLRC: ${tx}")
      Thread.sleep(1000)
      val transfers = Await.result(
        singleRequest(
          GetTransactionRecords
            .Req(
              owner = account2.getAddress,
              sort = SortingType.DESC,
              paging = Some(CursorPaging(cursor = 0, size = 50))
            ),
          "get_transactions"
        ).mapAs[GetTransactionRecords.Res].map(_.transactions),
        timeout.duration
      )
      transfers.size should be(1)

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
      val lrc_ba2_1 = ba2_1.balanceAndAllowanceMap(LRC_TOKEN.address)

      (BigInt(lrc_ba2_1.balance.toByteArray) - BigInt(
        lrc_ba2.balance.toByteArray
      )).toString() should be("1000" + "0" * LRC_TOKEN.decimals)
    }
  }

}
