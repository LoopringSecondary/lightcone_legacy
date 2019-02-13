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

import io.lightcone.persistence._
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent.Await

class BalanceAndTransferEventExtractorSpec
    extends CommonSpec
    with EthereumEventExtractorSupport {

  "ethereum balance update event and transfer event extractor actor test" must {
    "correctly extract balance update events and transfer events from ethereum blocks" in {
      val getBaMethod = "get_balance_and_allowance"
      val account0 = accounts.head
      val account2 = getUniqueAccountWithoutEth
      val account3 = getUniqueAccountWithoutEth
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
      info("transfer to account2 1000 LRC")
      Await.result(
        transferEth(account2.getAddress, "10")(account0),
        timeout.duration
      )
      Await.result(
        transferLRC(account2.getAddress, "1000")(account0),
        timeout.duration
      )
      expectTransfersRes(
        GetTransactionRecords
          .Req(
            owner = account2.getAddress,
            sort = SortingType.DESC,
            paging = Some(CursorPaging(cursor = 0, size = 50))
          ),
        (res: GetTransactionRecords.Res) => {
          res.transactions.size == 2
        }
      )
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
      transfers.size should be(2)

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

      Await.result(
        transferWETH(account3.getAddress, "10")(account2),
        timeout.duration
      )
      expectTransfersRes(
        GetTransactionRecords
          .Req(
            owner = account3.getAddress,
            sort = SortingType.DESC,
            paging = Some(CursorPaging(cursor = 0, size = 50))
          ),
        (res: GetTransactionRecords.Res) => {
          res.transactions.size == 1
        }
      )
      val transfers3 = Await.result(
        singleRequest(
          GetTransactionRecords
            .Req(
              owner = account3.getAddress,
              sort = SortingType.DESC,
              paging = Some(CursorPaging(cursor = 0, size = 50))
            ),
          "get_transactions"
        ).mapAs[GetTransactionRecords.Res].map(_.transactions),
        timeout.duration
      )

      transfers3.size should be(1)
      transfers3.head.header.get.txStatus.isTxStatusFailed should be(true)

      val transfers_2 = Await.result(
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
      transfers_2.size should be(3)
    }
  }

}
