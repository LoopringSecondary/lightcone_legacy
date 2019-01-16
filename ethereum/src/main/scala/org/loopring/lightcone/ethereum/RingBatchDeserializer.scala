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

package org.loopring.lightcone.ethereum

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

  val bitExtractor = new BitExtractor(Numeric.cleanHexPrefix(encoded))

  private var tableOffSet: Int = 0
  private var dataOffset: Int = 0

  def deserialize: Either[ErrorCode, RingBatch] =
    try {
      val version = bitExtractor.extractUint16(0)
      val numOrders = bitExtractor.extractUint16(2)
      val numRings = bitExtractor.extractUint16(4)
      val numSpendables = bitExtractor.extractUint16(6)

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
      case e: Throwable =>
        e.printStackTrace
        Left(ErrorCode.ERR_SERIALIZATION_ENCODED_DATA_INVALID)
    }

  private def setMiningData(miningTableOffset: Int) = {
    this.tableOffSet = miningTableOffset
    val feeRecipient = nextAddress
    val miner = nextAddress
    val sig = nextBytes
    // val sig = ""

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
      val ringSize = bitExtractor.extractUint8(ringOffset)
      var orderOffset = ringOffset
      val orderIndexes = (0 until ringSize) map { _ =>
        val orderIndex = bitExtractor.extractUint8(orderOffset)
        orderOffset += 1
        orderIndex
      }

      ringOffset += 8
      new RingBatch.Ring(orderIndexes)
    }

    ringBatch.copy(rings = rings)
  }

  private def getNextOffset() = {
    val offset = bitExtractor.extractUint16(tableOffSet)
    tableOffSet += 2
    offset
  }

  private def nextUint16() = getNextOffset

  private def nextInt16() = {
    val offset = bitExtractor.extractInt16(tableOffSet)
    tableOffSet += 2
    offset
  }

  private def nextUint32() = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      bitExtractor.extractUint32(dataOffset + offset)
    } else {
      0
    }
  }

  private def nextUint() = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      bitExtractor.extractUint(dataOffset + offset)
    } else {
      BigInt(0)
    }
  }

  private def nextAddress() = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      bitExtractor.extractAddress(dataOffset + offset)
    } else {
      ""
    }
  }

  private def nextBytes32() = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      "0x" + bitExtractor.extractBytesX(dataOffset + offset, 32)
    } else {
      "0x" + "0" * 64
    }
  }

  private def nextBytes() = {
    val offset = getNextOffset * 4
    if (offset > 0) {
      val len = bitExtractor.extractUint(dataOffset + offset).toInt
      "0x" + bitExtractor.extractBytesX(dataOffset + offset + 32, len)
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
      amountS = nextUint,
      amountB = nextUint,
      validSince = nextUint32
    )

    nextUint16() // tokenSpendableS, ignore
    nextUint16() // tokenSpendableB, ignore

    val params = new RawOrder.Params(
      dualAuthAddr = nextAddress,
      broker = nextAddress,
      orderInterceptor = nextAddress,
      wallet = nextAddress,
      validUntil = nextUint32,
      sig = nextBytes,
      dualAuthSig = nextBytes,
      allOrNone = nextUint16 > 0
    )

    val feeParams = new RawOrder.FeeParams(
      tokenFee = nextAddress,
      amountFee = nextUint,
      waiveFeePercentage = nextInt16,
      tokenSFeePercentage = nextUint16,
      tokenBFeePercentage = nextUint16,
      tokenRecipient = nextAddress,
      walletSplitPercentage = nextUint16
    )

    val erc1400Params = new RawOrder.ERC1400Params(
      tokenStandardS = TokenStandard.fromValue(nextUint16),
      tokenStandardB = TokenStandard.fromValue(nextUint16),
      tokenStandardFee = TokenStandard.fromValue(nextUint16),
      trancheS = nextBytes32,
      trancheB = nextBytes32,
      transferDataS = nextBytes
    )

    val order2 = order.copy(
      params = Some(params),
      feeParams = Some(feeParams),
      erc1400Params = Some(erc1400Params)
    )

    order2
  }

  class BitExtractor(data: String) {
    private def hex2Int(hex: String): Int = Integer.parseInt(hex, 16)

    def extractUint8(offset: Int): Int = hex2Int(extractBytesX(offset, 1))

    def extractUint16(offset: Int): Int = hex2Int(extractBytesX(offset, 2))

    def extractInt16(offset: Int): Int = {
      val hex = extractBytesX(offset, 2)
      val uint16 = BigInt(hex, 16)
      val resBigInt = if ((uint16 >> 15) == 1) {
        uint16 - BigInt("ffff", 16) - 1
      } else {
        uint16
      }

      resBigInt.toInt
    }

    def extractUint32(offset: Int): Int = hex2Int(extractBytesX(offset, 4))

    def extractUint(offset: Int): BigInt = {
      val hexStr = extractBytesX(offset, 32)
      BigInt(hexStr, 16)
    }

    def extractAddress(offset: Int) = "0x" + extractBytesX(offset, 20)

    def extractBytesX(
        offset: Int,
        numBytes: Int
      ) = {
      val start = offset * 2
      val end = start + numBytes * 2

      require(this.data.length > end, "substring index out of range.")
      this.data.substring(start, end)
    }

  }

}
