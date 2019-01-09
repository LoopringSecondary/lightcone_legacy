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

package org.loopring.lightcone.persistence.services

import org.loopring.lightcone.persistence.dals.TransactionRecordDalImpl
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto.TransactionRecord.RecordType
import org.loopring.lightcone.proto._
import scala.concurrent._
import scala.concurrent.duration._

class TransactionRecordServiceSpec
    extends ServiceSpec[TransactionRecordService] {
  def getService = new TransactionRecordServiceImpl()

  val blockchainScanRecordSeparate =
    config.getInt("separate.transaction_record")

  def createTables(): Future[Any] =
    Future {
      (0 until blockchainScanRecordSeparate).foreach { index =>
        new TransactionRecordDalImpl(index).createTable()
      }
    }

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
    service.saveRecord(r)
  }

  "saveRecord" must "save a record successfully" in {
    val owner = "0xBe4C1cb10C2Be76798c4186ADbbC34356b358b52"
    val result = for {
      saved <- testSave(
        "0x-hash1",
        TxStatus.TX_STATUS_SUCCESS,
        100L,
        1,
        0,
        owner,
        owner,
        "0x-to"
      )
      query <- service.getRecordsByOwner(
        owner,
        Some(GetTransactions.QueryType(RecordType.TRANSFER)),
        SortingType.ASC,
        CursorPaging(size = 10)
      )
    } yield query
    val res = Await.result(result.mapTo[Seq[TransactionRecord]], 5.second)
    res should not be empty
  }
}
