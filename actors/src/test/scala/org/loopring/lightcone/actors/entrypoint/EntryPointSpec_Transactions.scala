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

package org.loopring.lightcone.actors.entrypoint

import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core.EthereumEventAccessActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.EventAccessProvider
import org.loopring.lightcone.proto._
import scala.concurrent.{Await, Future}

class EntryPointSpec_Transactions
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "ethereum_event_access"]
                         |""".stripMargin)
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with EthereumEventAccessSupport {

  private def testSave(
      txHash: String,
      txStatus: TxStatus,
      blockNumber: Long,
      txIndex: Int = 0,
      logIndex: Int = 0,
      owner: String,
      txFrom: String,
      txTo: String
    ): Future[PersistTransactionRecord.Res] = {
    val header = EventHeader(
      txHash = txHash,
      txStatus = txStatus,
      blockNumber = blockNumber,
      txIndex = txIndex,
      logIndex = logIndex,
      txFrom = txFrom,
      txTo = txTo
    )
    val data = OrderFilledEvent(owner = "0x111")
    val r = TransactionRecord(
      header = Some(header),
      owner = owner,
      recordType = TransactionRecord.RecordType.TRANSFER,
      eventData = Some(
        TransactionRecord
          .EventData(TransactionRecord.EventData.Event.Filled(data))
      )
    )
    dbModule.transactionRecordService.saveRecord(r)
  }

  "save & query some events" must {
    "get the events" in {
      val txHash =
        "0x016331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val blockNumber = 70000001L
      val txFrom = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val txTo = "0xe7b95e3aefeb28d8a32a46e8c5278721dad39550"
      val header1 = EventHeader(
        txHash = txHash,
        txStatus = TxStatus.TX_STATUS_SUCCESS,
        blockHash = txHash,
        blockNumber = blockNumber,
        blockTimestamp = timeProvider.getTimeSeconds(),
        txFrom = txFrom,
        txTo = txTo,
        txIndex = 1,
        logIndex = 0
      )
      // 1. eth transfer
      actors.get(EthereumEventAccessActor.name) ! TransferEvent(
        header = Some(header1),
        owner = txFrom,
        from = txFrom,
        to = txTo,
        amount = ByteString.copyFrom("11", "utf-8")
      )
      actors.get(EthereumEventAccessActor.name) ! TransferEvent(
        header = Some(header1),
        owner = txTo,
        from = txFrom,
        to = txTo,
        amount = ByteString.copyFrom("11", "utf-8")
      )
      // 2. erc20 transfer
      val header2 = header1.copy(
        txIndex = 2,
        logIndex = 0,
        txHash =
          "0x026331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actors.get(EthereumEventAccessActor.name) ! TransferEvent(
        header = Some(header2),
        owner = txFrom,
        from = txFrom,
        to = txTo,
        token = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        amount = ByteString.copyFrom("11", "utf-8")
      )
      actors.get(EthereumEventAccessActor.name) ! TransferEvent(
        header = Some(header2),
        owner = txTo,
        from = txFrom,
        to = txTo,
        token = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        amount = ByteString.copyFrom("11", "utf-8")
      )

      // 3. cancelled
      val header3 = header1.copy(
        txIndex = 3,
        logIndex = 0,
        txHash =
          "0x036331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actors.get(EthereumEventAccessActor.name) ! OrdersCancelledEvent(
        header = Some(header3),
        owner = txFrom,
        orderHashes = Seq("0x1", "0x2")
      )

      // 4. cutoff
      val header4 = header1.copy(
        txIndex = 4,
        logIndex = 0,
        txHash =
          "0x046331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actors.get(EthereumEventAccessActor.name) ! CutoffEvent(
        header = Some(header4),
        owner = txTo,
        tradingPair = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        cutoff = timeProvider.getTimeSeconds()
      )

      // 5.1 save a order
      val orderHash = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val tokenS = "0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1"
      val tokenB = "0x8B75225571ff31B58F95C704E05044D5CF6B32BF"
      val response1 = dbModule.orderService.saveOrder(
        RawOrder(
          owner = txFrom,
          hash = orderHash,
          tokenS = tokenS,
          tokenB = tokenB,
          addressShardId = 1,
          marketHashId = 1
        )
      )
      val r1 = Await.result(
        response1.mapTo[Either[RawOrder, ErrorCode]],
        timeout.duration
      )
      // 5.2 filled
      val header5 = header1.copy(
        txIndex = 5,
        logIndex = 0,
        txHash =
          "0x056331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actors.get(EthereumEventAccessActor.name) ! OrderFilledEvent(
        header = Some(header5),
        owner = txFrom,
        orderHash = orderHash
      )
      actors.get(EthereumEventAccessActor.name) ! OrderFilledEvent(
        header = Some(header5),
        owner = txTo,
        orderHash = orderHash
      )

      // 6. mock failed
      actors.get(EthereumEventAccessActor.name) ! OrderFilledEvent(
        header = Some(header5),
        owner = txTo,
        orderHash = orderHash
      )

      Thread.sleep(5000)

      val fromIndex = EventAccessProvider.generateSequenceId(
        blockNumber,
        0,
        0
      )
      val paging: CursorPaging = CursorPaging(cursor = fromIndex, size = 50)
      val resonse2 = singleRequest(
        GetTransactions
          .Req(owner = txFrom, sort = SortingType.DESC, paging = Some(paging)),
        "get_transactions"
      )
      val r2 =
        Await.result(resonse2.mapTo[GetTransactions.Res], timeout.duration)
      assert(r2.records.length == 4)
    }
  }

}
