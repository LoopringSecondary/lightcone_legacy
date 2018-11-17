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

import scala.collection.mutable.{ HashMap ⇒ MMap }

trait RingSerializer {

  // 根据环路信息组装合约data
  def serialize(ring: Ring): String

}

class RingSerializerImpl(lrcAddress: String) extends RingSerializer {

  def serialize(ring: Ring): String = {
    ring.orders.foreach(o ⇒ assert(o.hash.nonEmpty))

    val helper = new RingSerializerHelper(lrcAddress, ring)
    helper.assemble()
  }

}

// warning: 代码顺序不能调整！！！！！！
private[lib] class RingSerializerHelper(lrcAddress: String, ring: Ring) {
  val ORDER_VERSION = 0
  val SERIALIZATION_VERSION = 0

  val datastream = ByteStream()
  val tablestream = ByteStream()
  val orderSpendableSMap = MMap.empty[String, Int]
  val orderSpendableFeeMap = MMap.empty[String, Int]

  def assemble(): String = {
    val numSpendables = setupSpendables

    datastream.addUint(0)
    createMiningTable()
    ring.orders.foreach(createOrderTable)

    val stream = ByteStream()
    stream.addUint16(SERIALIZATION_VERSION)
    stream.addUint16(ring.orders.length)
    stream.addUint16(ring.ringOrderIndex.length)
    stream.addUint16(numSpendables)
    stream.addHex(tablestream.getData)

    ring.ringOrderIndex.map(orderIdxs ⇒ {
      stream.addUint8(orderIdxs.length)
      orderIdxs.map(o ⇒ stream.addUint8(o))
      stream.addX(0, 8 - orderIdxs.length)
    })

    stream.addUint(0)
    stream.addHex(datastream.getData)

    stream.getData
  }

  def setupSpendables: Int = {
    var numSpendables = 0
    var ownerTokens = MMap.empty[String, Int]

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
  def createMiningTable(): Unit = {
    require(ring.miner.nonEmpty)

    val transactionOrigin = if (ring.transactionOrigin.nonEmpty) ring.transactionOrigin else ring.miner
    val feeRecipient = if (ring.feeReceipt.nonEmpty) ring.feeReceipt else transactionOrigin

    if (feeRecipient safeneq transactionOrigin) {
      insertOffset(datastream.addAddress(ring.feeReceipt))
    } else {
      insertDefault()
    }

    if (ring.miner safeneq feeRecipient) {
      insertOffset(datastream.addAddress(ring.miner))
    } else {
      insertDefault()
    }

    if (ring.sig.nonEmpty && (ring.miner safeneq transactionOrigin)) {
      insertOffset(datastream.addHex(createBytes(ring.sig)))
      addPadding()
    } else {
      insertDefault()
    }
  }

  def createOrderTable(order: Order): Unit = {
    addPadding()

    insertOffset(ORDER_VERSION)
    insertOffset(datastream.addAddress(order.owner))
    insertOffset(datastream.addAddress(order.tokenS))
    insertOffset(datastream.addAddress(order.tokenB))
    insertOffset(datastream.addUint(order.amountS, false))
    insertOffset(datastream.addUint(order.amountB, false))
    insertOffset(datastream.addUint32(order.validSince, false))

    orderSpendableSMap.get(order.hash) match {
      case Some(x: Int) ⇒ tablestream.addUint16(x.intValue())
      case _            ⇒ throw new Exception("ringGenerator get " + order.hash + "orderSpendableS failed")
    }
    orderSpendableFeeMap.get(order.hash) match {
      case Some(x: Int) ⇒ tablestream.addUint16(x.intValue())
      case _            ⇒ throw new Exception("ringGenerator get " + order.hash + "orderSpendableFee failed")
    }

    if (order.dualAuthAddress.nonEmpty) {
      insertOffset(datastream.addAddress(order.dualAuthAddress))
    } else {
      insertDefault()
    }

    // order.broker 默认占位
    insertDefault()

    // order.interceptor默认占位
    insertDefault()

    if (order.wallet.nonEmpty) {
      insertOffset(datastream.addAddress(order.wallet))
    } else {
      insertDefault()
    }

    if (order.validUntil > 0) {
      insertOffset(datastream.addUint32(order.validUntil, false))
    } else {
      insertDefault()
    }

    if (order.sig.nonEmpty) {
      insertOffset(datastream.addHex(createBytes(order.sig), false))
      addPadding()
    } else {
      insertDefault()
    }

    if (order.dualAuthSig.nonEmpty) {
      insertOffset(datastream.addHex(createBytes(order.dualAuthSig), false))
      addPadding()
    } else {
      insertDefault()
    }

    val allOrNone = if (order.allOrNone) 1 else 0
    tablestream.addUint16(allOrNone)

    if (order.feeToken.nonEmpty && (order.feeToken safeneq lrcAddress)) {
      insertOffset(datastream.addAddress(order.feeToken))
    } else {
      insertDefault()
    }

    if (order.feeAmount.signum > 0) {
      insertOffset(datastream.addUint(order.feeAmount, false))
    } else {
      insertDefault()
    }

    val waiveFeePercentage = if (order.waiveFeePercentage > 0) order.waiveFeePercentage else 0
    tablestream.addUint16(waiveFeePercentage)

    val tokenSFeePercentage = if (order.tokenSFeePercentage > 0) order.tokenSFeePercentage else 0
    tablestream.addUint16(tokenSFeePercentage)

    val tokenBFeePercentage = if (order.tokenBFeePercentage > 0) order.tokenBFeePercentage else 0
    tablestream.addUint16(tokenBFeePercentage)

    if (order.tokenReceipt.nonEmpty && (order.tokenReceipt safeneq order.owner)) {
      insertOffset(datastream.addAddress(order.tokenReceipt))
    } else {
      insertDefault()
    }

    val walletSplitPercentage = if (order.walletSplitPercentage > 0) order.walletSplitPercentage else 0
    tablestream.addUint16(walletSplitPercentage)
  }

  private def createBytes(data: String): String = {
    val bitstream = ByteStream()
    bitstream.addUint((data.length - 2) / 2)
    bitstream.addRawBytes(data)
    bitstream.getData
  }

  private def insertOffset(offset: Int): Unit = {
    assert(offset % 4 == 0)
    tablestream.addUint16(offset / 4)
  }

  private def insertDefault(): Unit = {
    tablestream.addUint16(0)
  }

  private def addPadding(): Unit = {
    if (datastream.length % 4 != 0) {
      datastream.addX(0, 4 - (datastream.length % 4))
    }
  }
}
