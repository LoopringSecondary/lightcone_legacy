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

package org.loopring.lightcone.lib

import org.web3j.utils.Numeric

trait RingDeserializer {

  // 将合约环路data解析为ring
  def deserialize(encoded: String): Ring

}

class RingDeserializerImpl(lrcAddress: String) extends RingDeserializer {

  def deserialize(encoded: String): Ring = {
    val helper = new RingDeserializerHelper(lrcAddress, encoded)
    helper.dissemble()
  }

}

private[lib] class RingDeserializerHelper(lrcAddress: String, encoded: String) {

  val dataparser: ByteParser = ByteParser(encoded)
  var dataOffset: Int = 0
  var tableOffset: Int = 0
  var spendableList = Seq.empty[String]

  val undefined = "0x0"

  def dissemble(): Ring = {
    val version = dataparser.extractUint16(0)
    val numOrders = dataparser.extractUint16(2)
    val numRings = dataparser.extractUint16(4)
    val numSpendables = dataparser.extractUint16(6)

    assert(version.equals(0), "Unsupported serialization format")
    assert(numSpendables > 0, "Invalid number of spendables")

    val miningDataPtr = 8
    val orderDataPtr = miningDataPtr + 3 * 2
    val ringDataPtr = orderDataPtr + (24 * numOrders) * 2
    val dataBlobPtr = ringDataPtr + (numRings * 9) + 32

    (1 to numSpendables).foreach(_ ⇒ spendableList +:= undefined)

    dataOffset = dataBlobPtr
    tableOffset = miningDataPtr

    val _feeRecipient = nextAddress
    val _miner = nextAddress
    val _sig = nextBytes
    val _orders = setupOrders(orderDataPtr, numOrders)
    val _rings = assembleRings(numRings, ringDataPtr, _orders)

    Ring(
      miner = _miner,
      feeReceipt = _feeRecipient,
      sig = _sig,
      orders = _orders,
      ringOrderIndex = _rings,
      transactionOrigin = "")
  }

  private def setupOrders(tablesPtr: Int, numOrders: Int): Seq[Order] = {
    tableOffset = tablesPtr
    (1 to numOrders).map(_ ⇒ assembleOrder())
  }

  private def assembleRings(numRings: Int, originOffset: Int, orders: Seq[Order]): Seq[Seq[Int]] = {
    var offset = originOffset

    (1 to numRings).map { _ ⇒
      val ringsize = dataparser.extractUint8(offset)
      val ring = assembleRing(ringsize, offset + 1, orders)
      offset += 1 + 8
      ring
    }
  }

  private def assembleRing(ringsize: Int, originOffset: Int, orders: Seq[Order]): Seq[Int] = {
    var offset = originOffset
    (1 to ringsize).map { _ ⇒
      val orderidx = dataparser.extractUint8(offset)
      offset += 1
      orderidx
    }
  }

  private def assembleOrder(): Order = {
    val _version = nextUint16
    val _owner = nextAddress
    val _tokenS = nextAddress
    val _tokenB = nextAddress
    val _amountS = Numeric.toHexString(nextUint.toByteArray)
    val _amountB = Numeric.toHexString(nextUint.toByteArray)
    val _validSince = nextUint32
    val _tokenSpendableS = spendableList.apply(nextUint16)
    val _tokenSpendableFee = spendableList.apply(nextUint16)
    val _dualAuthAddr = nextAddress
    val _broker = nextAddress
    val _orderInterceptor = nextAddress
    val _walletAddr = nextAddress
    val _validUntil = nextUint32
    val _sig = nextBytes
    val _dualAuthSig = nextBytes
    val _allOrNone = nextUint16 > 0
    val _feeToken = nextAddress
    val _feeAmount = Numeric.toHexString(nextUint.toByteArray)
    val _waiveFeePercentage = toInt16(nextUint16)
    val _tokenSFeePercentage = nextUint16
    val _tokenBFeePercentage = nextUint16
    val _tokenRecipient = nextAddress
    val _walletSplitPercentage = nextUint16

    val finalFeeToken = if (_feeToken.equals(undefined)) lrcAddress else _feeToken
    val finalTokenRecipient = if (_tokenRecipient.equals(undefined)) _owner else _tokenRecipient

    Order(
      owner = _owner,
      tokenS = _tokenS,
      tokenB = _tokenB,
      amountS = _amountS,
      amountB = _amountB,
      validSince = _validSince,
      validUntil = _validUntil,
      dualAuthAddress = _dualAuthAddr,
      wallet = _walletAddr,
      allOrNone = _allOrNone,
      feeToken = finalFeeToken,
      feeAmount = _feeAmount,
      tokenReceipt = finalTokenRecipient,
      walletSplitPercentage = _walletSplitPercentage,
      hash = "",
      sig = _sig,
      dualAuthSig = _dualAuthSig,
      broker = _broker,
      orderInterceptor = _orderInterceptor,
      waiveFeePercentage = _waiveFeePercentage,
      tokenSFeePercentage = _tokenSFeePercentage,
      tokenBFeePercentage = _tokenBFeePercentage,
      version = _version,
      tokenSpendableS = _tokenSpendableS,
      tokenSpendableFee = _tokenSpendableFee)
  }

  private def nextAddress: String = {
    val offset = tupple4GetNextOffset
    if (offset != 0) {
      dataparser.extractAddress(dataOffset + offset)
    } else {
      undefined
    }
  }

  private def nextUint: BigInt = {
    val offset = tupple4GetNextOffset
    if (offset != 0) {
      dataparser.extractUint(dataOffset + offset)
    } else {
      BigInt(0)
    }
  }

  private def nextUint16: Int = {
    getNextOffset
  }

  private def nextUint32: Int = {
    val offset = tupple4GetNextOffset
    if (offset != 0) {
      dataparser.extractUint32(dataOffset + offset)
    } else {
      0
    }
  }

  private def nextBytes: String = {
    val offset = tupple4GetNextOffset
    if (offset != 0) {
      val len = dataparser.extractUint(dataOffset + offset).intValue()
      Numeric.toHexString(dataparser.extractBytesX(dataOffset + offset + 32, len))
    } else {
      ""
    }
  }

  private def toInt16(x: BigInt): Int = {
    x.intValue()
  }

  private def tupple4GetNextOffset: Int = {
    getNextOffset * 4
  }

  private def getNextOffset: Int = {
    val offset = dataparser.extractUint16(tableOffset)
    tableOffset += 2
    offset
  }
}
