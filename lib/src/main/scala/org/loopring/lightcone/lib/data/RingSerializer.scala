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

package org.loopring.lightcone.lib.data

trait RingSerializer {

  // 根据环路信息组装合约data
  def serialize(ring: CRing): String
}

class RingSerializerImpl(lrcAddress: String) extends RingSerializer {

  def serialize(ring: CRing) = {
    ring.orders.foreach(o ⇒ assert(o.hash.nonEmpty))

    val helper = new RingSerializerHelper(lrcAddress, ring)
    helper.assemble()
  }
}

// warning: 代码顺序不能调整！！！！！！
private[lib] class RingSerializerHelper(lrcAddress: String, ring: CRing) {
  val ORDER_VERSION = 0
  val SERIALIZATION_VERSION = 0

  private val dataPacker = new BytesPacker()
  private val tablePacker = new BytesPacker()
  private var orderSpendableSMap = Map.empty[String, Int]
  private var orderSpendableFeeMap = Map.empty[String, Int]

  def assemble(): String = {
    val numSpendables = setupSpendables()

    dataPacker.addUint(0)
    createMiningTable()
    ring.orders.foreach(createOrderTable)

    val Packer = new BytesPacker()
    Packer.addUint16(SERIALIZATION_VERSION)
    Packer.addUint16(ring.orders.length)
    Packer.addUint16(ring.ringOrderIndex.length)
    Packer.addUint16(numSpendables)
    Packer.addHex(tablePacker.getPackedString)

    ring.ringOrderIndex.map(orderIdxs ⇒ {
      Packer.addUint8(orderIdxs.length)
      orderIdxs.map(o ⇒ Packer.addUint8(o))
      Packer.addX(0, 8 - orderIdxs.length)
    })

    Packer.addUint(0)
    Packer.addHex(dataPacker.getPackedString)

    Packer.getPackedString
  }

  private def setupSpendables(): Int = {
    var numSpendables = 0
    var ownerTokens = Map.empty[String, Int]

    ring.orders.foreach { order ⇒
      assert(order.hash.nonEmpty)

      val tokenFee = if (order.feeToken.nonEmpty) order.feeToken else lrcAddress

      val tokensKey = (order.owner + "-" + order.tokenS).toLowerCase
      ownerTokens.get(tokensKey) match {
        case Some(x: Int) ⇒
          orderSpendableSMap += order.hash -> x
        case _ ⇒
          ownerTokens += tokensKey -> numSpendables
          orderSpendableSMap += order.hash -> numSpendables
          numSpendables += 1
      }

      ownerTokens.get((order.owner + "-" + tokenFee).toLowerCase) match {
        case Some(x: Int) ⇒
          orderSpendableFeeMap += order.hash -> x
        case _ ⇒
          ownerTokens += tokensKey -> numSpendables
          orderSpendableFeeMap += order.hash -> numSpendables
          numSpendables += 1
      }
    }

    numSpendables
  }

  // 注意:
  // 1. 对于relay来说miner就是transactionOrigin
  private def createMiningTable(): Unit = {
    require(ring.transactionOrigin.nonEmpty)

    val feeRecipient =
      if (ring.feeReceipt.nonEmpty) ring.feeReceipt
      else ring.transactionOrigin

    val miner =
      if (ring.miner.nonEmpty) ring.miner
      else feeRecipient

    if (feeRecipient neqCaseInsensitive ring.transactionOrigin) {
      insertOffset(dataPacker.addAddress(ring.feeReceipt))
    } else {
      insertDefault()
    }

    if (miner neqCaseInsensitive feeRecipient) {
      insertOffset(dataPacker.addAddress(ring.miner))
    } else {
      insertDefault()
    }

    if (ring.sig.nonEmpty && (miner neqCaseInsensitive ring.transactionOrigin)) {
      insertOffset(dataPacker.addHex(createBytes(ring.sig), false))
      addPadding()
    } else {
      insertDefault()
    }
  }

  private def createOrderTable(order: COrder): Unit = {
    addPadding()

    insertOffset(ORDER_VERSION)
    insertOffset(dataPacker.addAddress(order.owner))
    insertOffset(dataPacker.addAddress(order.tokenS))
    insertOffset(dataPacker.addAddress(order.tokenB))
    insertOffset(dataPacker.addUint(order.amountS, false))
    insertOffset(dataPacker.addUint(order.amountB, false))
    insertOffset(dataPacker.addUint32(order.validSince, false))

    orderSpendableSMap.get(order.hash) match {
      case Some(x: Int) ⇒
        tablePacker.addUint16(x.intValue())
      case _ ⇒
        throw new Exception(s"ringGenerator get ${order.hash} orderSpendableS failed")
    }
    orderSpendableFeeMap.get(order.hash) match {
      case Some(x: Int) ⇒
        tablePacker.addUint16(x.intValue())
      case _ ⇒
        throw new Exception(s"ringGenerator get ${order.hash} orderSpendableFee failed")
    }

    if (order.dualAuthAddress.nonEmpty) {
      insertOffset(dataPacker.addAddress(order.dualAuthAddress))
    } else {
      insertDefault()
    }

    if (order.broker.nonEmpty) {
      insertOffset(dataPacker.addAddress(order.broker))
    } else {
      insertDefault()
    }

    // order.interceptor默认占位
    if (order.orderInterceptor.nonEmpty) {
      insertOffset(dataPacker.addAddress(order.orderInterceptor))
    } else {
      insertDefault()
    }

    if (order.wallet.nonEmpty) {
      insertOffset(dataPacker.addAddress(order.wallet))
    } else {
      insertDefault()
    }

    if (order.validUntil > 0) {
      insertOffset(dataPacker.addUint32(order.validUntil, false))
    } else {
      insertDefault()
    }

    if (order.sig.nonEmpty) {
      insertOffset(dataPacker.addHex(createBytes(order.sig), false))
      addPadding()
    } else {
      insertDefault()
    }

    if (order.dualAuthSig.nonEmpty) {
      insertOffset(dataPacker.addHex(createBytes(order.dualAuthSig), false))
      addPadding()
    } else {
      insertDefault()
    }

    val allOrNone = if (order.allOrNone) 1 else 0
    tablePacker.addUint16(allOrNone)

    if (order.feeToken.nonEmpty &&
      (order.feeToken neqCaseInsensitive lrcAddress)) {
      insertOffset(dataPacker.addAddress(order.feeToken))
    } else {
      insertDefault()
    }

    if (order.feeAmount.signum > 0) {
      insertOffset(dataPacker.addUint(order.feeAmount, false))
    } else {
      insertDefault()
    }

    val waiveFeePercentage =
      if (order.waiveFeePercentage > 0) order.waiveFeePercentage
      else 0

    tablePacker.addUint16(waiveFeePercentage)

    val tokenSFeePercentage =
      if (order.tokenSFeePercentage > 0) order.tokenSFeePercentage
      else 0

    tablePacker.addUint16(tokenSFeePercentage)

    val tokenBFeePercentage =
      if (order.tokenBFeePercentage > 0) order.tokenBFeePercentage
      else 0

    tablePacker.addUint16(tokenBFeePercentage)

    if (order.tokenReceipt.nonEmpty &&
      (order.tokenReceipt neqCaseInsensitive order.owner)) {
      insertOffset(dataPacker.addAddress(order.tokenReceipt))
    } else {
      insertDefault()
    }

    val walletSplitPercentage =
      if (order.walletSplitPercentage > 0) order.walletSplitPercentage
      else 0

    tablePacker.addUint16(walletSplitPercentage)
  }

  private def createBytes(data: String): String = {
    val bitPacker = new BytesPacker()
    bitPacker.addUint((data.length - 2) / 2)
    bitPacker.addRawBytes(data)
    bitPacker.getPackedString
  }

  private def insertOffset(offset: Int): Unit = {
    assert(offset % 4 == 0)
    tablePacker.addUint16(offset / 4)
  }

  private def insertDefault(): Unit = {
    tablePacker.addUint16(0)
  }

  private def addPadding(): Unit = {
    if (dataPacker.length % 4 != 0) {
      dataPacker.addX(0, 4 - (dataPacker.length % 4))
    }
  }
}
