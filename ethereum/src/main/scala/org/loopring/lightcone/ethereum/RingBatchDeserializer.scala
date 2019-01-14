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

package org.loopring.lightcone.ethereum.data

import org.loopring.lightcone.proto._
import org.loopring.lightcone.ethereum._
import com.google.protobuf.ByteString
import org.web3j.utils.Numeric

trait RingBatchDeserializer {
  def deserialize: Either[ErrorCode, RingBatch]
}

class SimpleRingBatchDeserializer(encoded: String = "")
    extends RingBatchDeserializer {
  import ethereum._

  val dataStream = new Bitstream(encoded)

  private var tableOffSet: Int = 0
  private var dataOffset: Int = 0

  def deserialize: Either[ErrorCode, RingBatch] =
    try {
      val version = dataStream.extractUint16(0)
      val numOrders = dataStream.extractUint16(2)
      val numRings = dataStream.extractUint16(4)
      val numSpendables = dataStream.extractUint16(6)

      val miningTableOffset = 8
      val orderTableOffset = miningTableOffset + 3 * 2
      val ringDataOffset = orderTableOffset + (30 * 2) * numOrders
      dataOffset = ringDataOffset + (9 * numRings) + 32

      val ringBatchWithMiningData = setMiningData(miningTableOffset)
      val ringBatchWithOrders =
        setupOrders(
          ringBatchWithMiningData,
          orderTableOffset,
          numOrders
        )
      val ringBatchWithRings =
        assembleRings(ringBatchWithOrders, ringDataOffset, numRings)

      Right(ringBatchWithRings)
    } catch {
      case _: Throwable => Left(ErrorCode.ERR_DESERIALIZE_INVALID_ENCODED_DATA)
    }

  private def setMiningData(miningTableOffset: Int) = {
    this.tableOffSet = miningTableOffset
    val feeRecipient = nextAddress
    val miner = nextAddress
    val sig = nextBytes

    new RingBatch(feeRecipient = feeRecipient, miner = miner, sig = sig)
  }

  private def setupOrders(
      ringBatch: RingBatch,
      orderTableOffset: Int,
      numOrders: Int
    ) = {
    this.tableOffSet = orderTableOffset
    val orders = (0 until numOrders).map(i => assembleOrder)
    ringBatch.copy(orders = orders)
  }

  private def assembleRings(
      ringBatch: RingBatch,
      ringDataOffset: Int,
      numRings: Int
    ) = {
    var ringOffset = ringDataOffset
    val rings = (0 until numRings) map { _ =>
      ringOffset += 1
      val ringSize = dataStream.extractUint8(ringOffset)
      var orderOffset = ringOffset
      val orderIndexes = (0 until ringSize) map { _ =>
        val orderIndex = dataStream.extractUint8(orderOffset)
        orderOffset += 1
        orderIndex
      }

      ringOffset += 8
      new RingBatch.Ring(orderIndexes)
    }

    ringBatch.copy(rings = rings)
  }

  private def getNextOffset = {
    val offset = dataStream.extractUint16(tableOffSet)
    tableOffSet += 2
    offset
  }

  private def nextUint16 = getNextOffset

  private def nextUint = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      dataStream.extractUint(dataOffset + offset)
    } else {
      ByteString.EMPTY
    }
  }

  private def nextAddress = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      dataStream.extractAddress(dataOffset + offset)
    } else {
      ""
    }
  }

  private def nextBytes = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      val len = dataStream.extractUint(dataOffset + offset).toInt
      "0x" + dataStream.extractBytesX(dataOffset + offset + 32, len)
    } else {
      ""
    }
  }

  private def assembleOrder = {
    val order = new RawOrder(
      version = nextUint16,
      owner = nextAddress,
      tokenS = nextAddress,
      tokenB = nextAddress,
      amountS = nextUint
    )

    order
  }

}
