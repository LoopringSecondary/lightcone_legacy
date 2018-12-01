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

import org.web3j.crypto.{ Hash ⇒ Web3Hash }
import org.web3j.utils.Numeric
import org.loopring.lightcone.proto.core._

trait RingBatchGenerator {
  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch
}

// TODO(kongliang): implement and test this class
class RingBatchGeneratorImpl(context: XRingBatchContext)
  extends RingBatchGenerator {

  class BytesPacker() {
    private val ADDRESS_LENGTH = 20
    private var data: String = ""

    def getPackedString() = {
      if (data.length.equals(0)) "0x0"
      else "0x" + data
    }

    def getPackedBytes() = Numeric.hexStringToByteArray(data)
    def length() = data.length / 2

    def addAddress(x: String, forceAppend: Boolean = false) =
      insert(
        Numeric.toHexStringNoPrefixZeroPadded(
          Numeric.toBigInt(x),
          ADDRESS_LENGTH * 2
        ),
        forceAppend
      )

    def addUint16(num: BigInt, forceAppend: Boolean = true) =
      addBigInt(num, 2, forceAppend)

    def addUint32(num: BigInt, forceAppend: Boolean = true) =
      addBigInt(num, 4, forceAppend)

    def addUint(num: BigInt, forceAppend: Boolean = true) =
      addBigInt(num, 32, forceAppend)

    def addUintStr(numStr: String, forceAppend: Boolean = true) =
      addBigIntStr(numStr, 32, forceAppend)

    def addX(num: BigInt, numBytes: Int, forceAppend: Boolean = true) =
      addBigInt(num, numBytes, forceAppend)

    def addBoolean(b: Boolean, forceAppend: Boolean = true) =
      addBigInt(if (b) 1 else 0, 1, forceAppend)

    def addHex(str: String, forceAppend: Boolean = true) =
      insert(Numeric.cleanHexPrefix(str), forceAppend)

    def addRawBytes(str: String, forceAppend: Boolean = true) =
      insert(Numeric.cleanHexPrefix(str), forceAppend)

    private def padString(str: String, numBytes: Int) = {
      val cleanStr = Numeric.cleanHexPrefix(str)
      val targetLen = numBytes * 2
      if (cleanStr.length > targetLen) {
        throw new IllegalArgumentException(str + " is too long for padding")
      }
      val paddingLen = targetLen - cleanStr.length
      "0" * paddingLen + cleanStr
    }

    // TODO(kongliang): 负数问题
    private def addBigInt(
      num: BigInt,
      numBytes: Int,
      forceAppend: Boolean = true
    ) =
      insert(
        Numeric.toHexStringNoPrefixZeroPadded(
          num.bigInteger,
          numBytes * 2
        ),
        forceAppend
      )

    private def addBigIntStr(
      numStr: String,
      numBytes: Int,
      forceAppend: Boolean = true
    ) = {
      insert(padString(numStr, numBytes), forceAppend)
    }

    private def insert(x: String, forceAppend: Boolean): Int = {
      var offset = length()

      if (!forceAppend) {
        // Check if the data we're inserting is already available in the bitstream.
        // If so, return the offset to the location.
        var start = 0
        while (start != -1) {
          start = data.indexOf(x, start)
          if (start != -1) {
            if ((start % 2) == 0) {
              offset = start / 2
              return offset
            } else {
              start += 1
            }
          }
        }
      }

      data ++= x
      offset
    }
  }

  object OrderHelper {
    def calculateHash(order: XRawOrder) = {
      val packer = new BytesPacker
      packer.addUintStr(order.amountS.toString)
      packer.addUintStr(order.amountB.toString)
      packer.addUintStr(order.feeAmount.toString)
      packer.addUint(BigInt(order.validSince))
      packer.addUint(BigInt(order.validUntil))
      packer.addAddress(order.owner, true)
      packer.addAddress(order.tokenS, true)
      packer.addAddress(order.tokenB, true)
      packer.addAddress(order.dualAuthAddress, true)
      packer.addAddress(order.broker, true)
      packer.addAddress(order.orderInterceptor, true)
      packer.addAddress(order.wallet, true)
      packer.addAddress(order.tokenRecipient, true)
      packer.addAddress(order.feeToken, true)
      packer.addUint16(order.walletSplitPercentage)
      packer.addUint16(order.tokenSFeePercentage)
      packer.addUint16(order.tokenBFeePercentage)
      packer.addBoolean(order.allOrNone)

      order.copy(hash = Numeric.toHexString(Web3Hash.sha3(packer.getPackedBytes)))
    }

    def sign(order: XRawOrder) = {
      order.copy()
    }
  }

  def sign(xRingBatch: XRingBatch) = {
    xRingBatch
  }

  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch = {
    val ordersDistinctedMap = orders
      .flatten
      .map(OrderHelper.calculateHash)
      .map(o ⇒ o.hash -> o)
      .toMap

    val ordersDistinctedSeq = ordersDistinctedMap
      .map(_._2)
      .map(OrderHelper.sign)

    val ordersHashIndexMap = ordersDistinctedSeq
      .zipWithIndex
      .toMap

    val xringBatch = new XRingBatch()
      .withFeeRecipient(context.feeRecipient)
      .withMiner(context.miner)

    sign(xringBatch)
  }
}
