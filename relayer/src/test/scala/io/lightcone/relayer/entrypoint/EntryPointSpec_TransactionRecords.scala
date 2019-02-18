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

package io.lightcone.relayer.entrypoint

import com.google.protobuf.ByteString
import io.lightcone.ethereum.event._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._
import io.lightcone.relayer.validator.TransactionRecordMessageValidator
import io.lightcone.relayer.data.TransactionRecord.EventData
import io.lightcone.relayer.data._
import io.lightcone.persistence._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.relayer.base._
import scala.concurrent.Await
import TransactionRecord.EventData.Event
import TransactionRecord.RecordType._

class EntryPointSpec_TransactionRecords
    extends CommonSpec
    with DatabaseModuleSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with EthereumTransactionRecordSupport {

  "save & query some events" must {
    "get the events record correctly" in {
      val txHash =
        "0x016331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      val blockNumber = 90000001L
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
        txIndex = 1
      )
      def actor = actors.get(TransactionRecordMessageValidator.name)
      info("save eth transfer for both sender and receiver")
      actor ! TransferEvent(
        header = Some(header1),
        owner = txFrom,
        from = txFrom,
        to = txTo,
        token = "0",
        amount = ByteString.copyFrom("11", "utf-8")
      )

      actor ! TransferEvent(
        header = Some(header1.copy(eventIndex = 1)),
        owner = txTo,
        from = txFrom,
        to = txTo,
        token = "0",
        amount = ByteString.copyFrom("11", "utf-8")
      )
      info("save erc20 transfer for both sender and receiver")
      val header2 = header1.copy(
        txIndex = 2,
        logIndex = 0,
        txHash =
          "0x026331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actor ! TransferEvent(
        header = Some(header2),
        owner = txFrom,
        from = txFrom,
        to = txTo,
        token = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        amount = ByteString.copyFrom("11", "utf-8")
      )
      actor ! TransferEvent(
        header = Some(header2.copy(eventIndex = 1)),
        owner = txTo,
        from = txFrom,
        to = txTo,
        token = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        amount = ByteString.copyFrom("11", "utf-8")
      )

      info("save cancelled from sender")
      val header3 = header1.copy(
        txIndex = 3,
        logIndex = 0,
        txHash =
          "0x036331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actor ! OrdersCancelledEvent(
        header = Some(header3),
        owner = txFrom,
        orderHashes = Seq("0x1", "0x2")
      )

      info("save cutoff from receiver")
      val header4 = header1.copy(
        txIndex = 4,
        logIndex = 0,
        txHash =
          "0x046331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actor ! CutoffEvent(
        header = Some(header4),
        owner = txTo,
        marketHash = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
        cutoff = timeProvider.getTimeSeconds()
      )

      info("save a order and submit 2 orderFilledEvents (will query order)")
      val orderHash = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val tokenS = "0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1"
      val tokenB = "0x8B75225571ff31B58F95C704E05044D5CF6B32BF"
      val response1 = dbModule.orderService.saveOrder(
        RawOrder(
          owner = txFrom,
          hash = orderHash,
          tokenS = tokenS,
          tokenB = tokenB,
          accountEntityId = 1,
          marketEntityId = 1
        )
      )
      val r1 = Await.result(
        response1.mapTo[Either[RawOrder, ErrorCode]],
        timeout.duration
      )
      val header5 = header1.copy(
        txIndex = 5,
        logIndex = 0,
        txHash =
          "0x056331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
      )
      actor ! OrderFilledEvent(
        header = Some(header5),
        owner = txFrom,
        orderHash = orderHash
      )
      actor ! OrderFilledEvent(
        header = Some(header5.copy(eventIndex = 1)),
        owner = txTo,
        orderHash = orderHash
      )

      info("test some failed condition (duplicated sequenceId)")
      actor ! OrderFilledEvent(
        header = Some(header5),
        owner = txTo,
        orderHash = orderHash
      )

      info("test some failed condition (invalid sequenceId)")
      val header6 = header1.copy(
        blockNumber = 100,
        txIndex = 10000,
        logIndex = 20000,
        eventIndex = 30000
      )
      actor ! OrderFilledEvent(
        header = Some(header6),
        owner = txTo,
        orderHash = orderHash
      )

      Thread.sleep(5000)

      info("get_transactions with txFrom to check saved record num")
      val fromIndex = EventHeader(blockNumber = blockNumber).sequenceId
      val paging: CursorPaging = CursorPaging(cursor = fromIndex, size = 50)
      val resonse2 = singleRequest(
        GetTransactionRecords
          .Req(owner = txFrom, sort = SortingType.DESC, paging = Some(paging)),
        "get_transactions"
      )
      val r2 =
        Await.result(
          resonse2.mapTo[GetTransactionRecords.Res],
          timeout.duration
        )
      assert(r2.transactions.length == 4)
      r2.transactions.foreach {
        _.eventData.getOrElse(EventData()).event match {
          case Event.Transfer(e) if Address(e.token).isZero =>
            assert(
              e.header
                .getOrElse(EventHeader())
                .txHash == "0x016331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
            )
          case Event.Transfer(e) if !Address(e.token).isZero =>
            assert(
              e.header
                .getOrElse(EventHeader())
                .txHash == "0x026331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
            )
          case Event.OrderCancelled(e) =>
            assert(
              e.header
                .getOrElse(EventHeader())
                .txHash == "0x036331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
            )
          case Event.Cutoff(e) => assert(false)
          case Event.Filled(e) =>
            assert(
              e.header
                .getOrElse(EventHeader())
                .txHash == "0x056331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"
            )
          case _ => assert(false)
        }
      }

      info("get_transactions with txTo to check saved record num")
      val resonse3 = singleRequest(
        GetTransactionRecordCount
          .Req(
            owner = txTo,
            queryType = Some(
              GetTransactionRecords
                .QueryType(ERC20_TRANSFER)
            )
          ),
        "get_transaction_count"
      )
      val r3 =
        Await.result(
          resonse3.mapTo[GetTransactionRecordCount.Res],
          timeout.duration
        )
      assert(r3.count === 1)

      info("get_transactions some bad request: invalid parameters: cursor")
      val paging1: CursorPaging = CursorPaging(cursor = -1, size = 50)
      val resonse4 = singleRequest(
        GetTransactionRecords
          .Req(owner = txFrom, sort = SortingType.DESC, paging = Some(paging1)),
        "get_transactions"
      )
      try {
        Await.result(
          resonse4.mapAs[GetTransactionRecords.Res],
          timeout.duration
        )
        assert(false)
      } catch {
        case e: ErrorException =>
          if (e.getMessage()
                .indexOf("Invalid parameter cursor of paging:-1") > -1)
            assert(true)
          else assert(false)
        case _: Throwable => assert(false)
      }

      info("get_transactions some bad request: invalid parameters: size")
      val paging2: CursorPaging = CursorPaging(cursor = 1, size = 5000)
      val resonse5 = singleRequest(
        GetTransactionRecords
          .Req(owner = txFrom, sort = SortingType.DESC, paging = Some(paging2)),
        "get_transactions"
      )
      try {
        Await.result(
          resonse5.mapAs[GetTransactionRecords.Res],
          timeout.duration
        )
        assert(false)
      } catch {
        case e: ErrorException =>
          if (e.getMessage()
                .indexOf("Parameter size of paging is larger than 50") > -1)
            assert(true)
          else assert(false)
        case _: Throwable => assert(false)
      }

      info("get_transactions some bad request: empty owner")
      val resonse6 = singleRequest(
        GetTransactionRecords
          .Req(sort = SortingType.DESC, paging = Some(paging)),
        "get_transactions"
      )
      try {
        Await.result(
          resonse6.mapAs[GetTransactionRecords.Res],
          timeout.duration
        )
        assert(false)
      } catch {
        case e: ErrorException =>
          if (e.getMessage()
                .indexOf("Parameter owner could not be empty") > -1)
            assert(true)
          else assert(false)
        case _: Throwable => assert(false)
      }
    }
  }

  "test sequenceId" must {
    "get sequenceId correctly" in {
      val correctHeader = EventHeader(
        blockNumber = 8000000,
        txIndex = 1,
        logIndex = 2,
        eventIndex = 3
      )
      // 1. correct
      val s1 = correctHeader.sequenceId
      val max = EventHeader(
        blockNumber = 500000000,
        txIndex = 4095,
        logIndex = 4095,
        eventIndex = 1023
      ).sequenceId
      assert(s1 == 137438953476196355L && max == 8589934609179869183L)
      // 2. invalid
      try {
        correctHeader.copy(blockNumber = 500000001).sequenceId
        assert(false)
      } catch {
        case e: ErrorException
            if e.getMessage().indexOf("blockNumber >= 500000000") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }
      try {
        correctHeader.copy(txIndex = 4096).sequenceId
        assert(false)
      } catch {
        case e: ErrorException
            if e.getMessage().indexOf("txIndex or logIndex >= 4096") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }
      try {
        correctHeader.copy(logIndex = 4096).sequenceId
        assert(false)
      } catch {
        case e: ErrorException
            if e.getMessage().indexOf("txIndex or logIndex >= 4096") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }
      try {
        correctHeader.copy(eventIndex = 1024).sequenceId
        assert(false)
      } catch {
        case e: ErrorException
            if e.getMessage().indexOf("eventIndex >= 1024") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }

    }
  }
}
