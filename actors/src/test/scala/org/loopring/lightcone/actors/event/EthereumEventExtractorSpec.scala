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
import org.loopring.lightcone.actors.core.TransactionRecordActor
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto._
import akka.pattern._

import scala.concurrent.Await

class EthereumEventExtractorSpec
    extends CommonSpec
    with DatabaseModuleSupport
    with EthereumSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with OrderCutoffSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with MetadataManagerSupport
    with DatabaseQueryMessageSupport
    with EthereumTransactionRecordSupport
    with EthereumEventExtractorSupport
    with OrderGenerateSupport {
  "ethereum event extractor actor test" must {
    "correctly extract all kinds events from ethereum blocks" in {

      def ethereumAccessActor = actors.get(EthereumAccessActor.name)
      def transactionRecordActor = actors.get(TransactionRecordActor.name)

      val getBaMethod = "get_balance_and_allowance"
      val submit_order = "submit_order"
      val account0 = accounts.head
      val account1 = accounts(1)
      val getOrderBook1 = GetOrderbook.Req(
        0,
        100,
        Some(MarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )

      val order1 = createRawOrder()(account1)
      val order2 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = BigInt(order1.amountS.toByteArray),
        amountS = BigInt(order1.amountB.toByteArray)
      )(account0)

      val ba1 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account0.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )

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
      val lrc_ba1 = ba1.balanceAndAllowanceMap(LRC_TOKEN.address)
      val lrc_ba2 = ba2.balanceAndAllowanceMap(LRC_TOKEN.address)
      info("transfer to account1 1000 LRC")
      Await.result(
        transferLRC(account1.getAddress, "1000")(account0),
        timeout.duration
      )
      info(s"${account1.getAddress} approve LRC")
      Await.result(approveLRCToDelegate("1000000")(account1), timeout.duration)
      Thread.sleep(1000)
      val transfers = Await.result(
        singleRequest(
          GetTransactionRecords
            .Req(
              owner = account0.getAddress,
              sort = SortingType.DESC,
              paging = Some(CursorPaging(cursor = 0, size = 50))
            ),
          "get_transactions"
        ).mapAs[GetTransactionRecords.Res].map(_.transactions),
        timeout.duration
      )
      transfers.size should be(1)

      val ba1_1 = Await.result(
        singleRequest(
          GetBalanceAndAllowances.Req(
            account0.getAddress,
            tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
          ),
          getBaMethod
        ).mapAs[GetBalanceAndAllowances.Res],
        timeout.duration
      )

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

      val lrc_ba1_1 = ba1_1.balanceAndAllowanceMap(LRC_TOKEN.address)
      val lrc_ba2_1 = ba2_1.balanceAndAllowanceMap(LRC_TOKEN.address)

      info(
        s"${account1.getAddress} allowance is ${BigInt(lrc_ba2_1.allowance.toByteArray)}"
      )

      (BigInt(lrc_ba1.balance.toByteArray) - BigInt(
        lrc_ba1_1.balance.toByteArray
      )).toString() should be("1000" + "0" * LRC_TOKEN.decimals)
      (BigInt(lrc_ba2_1.balance.toByteArray) - BigInt(
        lrc_ba2.balance.toByteArray
      )).toString() should be("1000" + "0" * LRC_TOKEN.decimals)

      (BigInt(lrc_ba2_1.allowance.toByteArray) - BigInt(
        lrc_ba2.allowance.toByteArray
      )).toString() should be("1000000" + "0" * LRC_TOKEN.decimals)
      info("submit the first order.")
      val orderRes1 =
        Await.result(
          singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
            .mapAs[SubmitOrder.Res],
          timeout.duration
        )
      Thread.sleep(1000)

      info("this order must be saved in db.")
      val getOrder = Await.result(
        dbModule.orderService.getOrder(order1.hash),
        timeout.duration
      )

      getOrder match {
        case Some(order) =>
          assert(order.sequenceId > 0)
        case None => assert(false)
      }

      val orderbookRes1 = Await.result(
        singleRequest(getOrderBook1, "orderbook").mapAs[GetOrderbook.Res],
        timeout.duration
      )
      println(orderbookRes1.getOrderbook)
//      val orderbookRes1 = expectOrderbookRes(
//        getOrderBook1,
//        (orderbook: Orderbook) => orderbook.sells.nonEmpty
//      )
//
//      orderbookRes1 match {
//        case Some(Orderbook(lastPrice, sells, buys)) =>
//          info(s"sells:${sells}, buys:${buys}")
//          assert(buys.isEmpty)
//          assert(sells.size == 1)
//          val head = sells.head
//          assert(head.amount.toDouble == "10".toDouble)
//          assert(head.price.toDouble == "10".toDouble)
//          assert(head.total.toDouble == "1".toDouble)
//        case _ => assert(false)
//      }

    }

  }
}
