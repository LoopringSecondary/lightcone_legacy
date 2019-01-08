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

import com.google.protobuf.ByteString
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.dals.BlockchainScanRecordDalImpl
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto._
import scala.concurrent._
import scala.concurrent.duration._

class BlockchainScanRecordServiceSpec
    extends ServiceSpec[BlockchainScanRecordService] {
  def getService = new BlockchainScanRecordServiceImpl()

  val blockchainScanRecordSeparate =
    config.getInt("separate.blockchain_scan_record")

  def createTables(): Future[Any] =
    Future {
      (0 until blockchainScanRecordSeparate).foreach { index =>
        new BlockchainScanRecordDalImpl(index).createTable()
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
    ): Future[PersistBlockchainRecord.Res] = {
    val header = EventHeader(
      txHash = txHash,
      txStatus = txStatus,
      blockNumber = blockNumber,
      txIndex = txIndex,
      logIndex = logIndex,
      txFrom = txFrom,
      txTo = txTo
    )
    val data = OrderFilledEvent(owner = "0x111").toByteArray

    OrderFilledEvent.parseFrom(data)

    val r = BlockchainRecordData(
      header = Some(header),
      owner = owner,
      recordType = BlockchainRecordData.RecordType.TRANSFER,
      eventData = ByteString.copyFrom(data)
    )
    service.saveRecord(r)
  }

  "saveRecord" must "save a record successfully" in {
    val owner = "0x-submitrecord-01"
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
        SortingType.ASC,
        CursorPaging(size = 10)
      )
    } yield query
    val res = Await.result(result.mapTo[Seq[BlockchainRecordData]], 5.second)
    res should not be empty
  }
}
