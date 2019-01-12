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

trait RingBatchDeserializer {
  def deserialize(encoded: String): Either[ErrorCode, RingBatch]
}

class SimpleRingBatchDeserializer extends RingBatchDeserializer {

  def deserialize(encoded: String): Either[ErrorCode, RingBatch] = {
    val stream = new Bitstream(encoded)

    try {
      val version = stream.extractUint16(0)
      val numOrders = stream.extractUint16(2)
      val numRings = stream.extractUint16(4)
      val numSpendables = stream.extractUint16(6)

      val miningDataOffset = 8
      val orderDataOffset = miningDataOffset + 3 * 2
      val ringDataOffset = orderDataOffset + (30 * 2) * numOrders
      val dataBlobOffset = ringDataOffset + (9 * numRings) + 32

      val ringBatchWithMiningData = setMiningData(encoded, miningDataOffset)
      val ringBatchWithOrders =
        setupOrders(
          encoded,
          ringBatchWithMiningData,
          orderDataOffset,
          numOrders
        )
      val ringBatchWithRings =
        assembleRings(encoded, ringBatchWithOrders, ringDataOffset, numRings)

      Right(ringBatchWithRings)
    } catch {
      case _: Throwable => Left(ErrorCode.ERR_DESERIALIZE_INVALID_ENCODED_DATA)
    }
  }

  def setMiningData(
      encoded: String,
      miningDataOffset: Int
    ) = {
    ???
  }

  def setupOrders(
      encoded: String,
      ringBatch: RingBatch,
      orderDataOffset: Int,
      numOrders: Int
    ) = {
    ???
  }

  def assembleRings(
      encoded: String,
      ringBatch: RingBatch,
      ringDataOffset: Int,
      numRings: Int
    ) = {
    ???
  }
}
