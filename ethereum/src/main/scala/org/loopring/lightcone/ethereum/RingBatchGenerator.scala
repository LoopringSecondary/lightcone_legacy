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

import org.web3j.crypto._
import org.web3j.utils.Numeric
import org.web3j.crypto.WalletUtils.isValidAddress
import com.google.protobuf.ByteString

import org.loopring.lightcone.proto._

trait RingBatchGenerator {
  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch
  def toSubmitableParamStr(xRingBatch: XRingBatch): String
}

// TODO(kongliang): implement and test this class
class RingBatchGeneratorImpl(context: XRingBatchContext)
  extends RingBatchGenerator {
  val OrderVersion = 0
  val SerializationVersion = 0

  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch = {
    orders.flatten.foreach(println)

    val orderValidator = new RawOrderValidatorImpl

    val ordersDistinctedMap = orders
      .flatten
      .map(o ⇒ setupOrderDefaults(o))
      .map(o ⇒ orderValidator.calculateOrderHash(o) -> o)
      .toMap

    val ordersDistinctedSeq = ordersDistinctedMap
      .map(_._2)
      .toSeq

    val ordersHashIndexMap = ordersDistinctedSeq
      .map(_.hash)
      .zipWithIndex
      .toMap

    val xrings = orders.map(orders ⇒ {
      val orderIndexes = orders.map(o ⇒ ordersHashIndexMap(o.hash))
      new XRingBatch.XRing(orderIndexes)
    })

    val xRingBatch = new XRingBatch()
      .withFeeRecipient(context.feeRecipient)
      .withMiner(context.miner)
      .withRings(xrings)
      .withOrders(ordersDistinctedSeq)
      .withSignAlgorithm(XSigningAlgorithm.ALGO_ETHEREUM)
      .withTransactionOrigin(context.transactionOrigin)

    sign(xRingBatch)
  }

  def toSubmitableParamStr(xRingBatch: XRingBatch): String = {
    val tokenSpendables = xRingBatch.orders.map(order ⇒
      Seq((order.owner + order.tokenS), (order.owner + order.feeParams.get.tokenFee)))
      .flatten
      .distinct
      .zipWithIndex
      .toMap

    val data = new Bitstream
    val tables = new Bitstream

    data.addUint(BigInt(0), true)
    setupMiningInfo(xRingBatch, data, tables)

    xRingBatch.orders.foreach(order ⇒ setupOrderInfo(data, tables, order, tokenSpendables))

    val paramStream = new Bitstream
    paramStream.addUint16(SerializationVersion)
    paramStream.addUint16(xRingBatch.orders.length)
    paramStream.addUint16(xRingBatch.rings.length)
    paramStream.addUint16(tokenSpendables.size)

    paramStream.addHex(tables.getData)
    xRingBatch.rings.foreach(ring ⇒ {
      val orderIndexes = ring.orderIndexes
      paramStream.addNumber(BigInt(orderIndexes.length), 1, true)
      orderIndexes.foreach(i ⇒ paramStream.addNumber(BigInt(i), 1, true))
      paramStream.addNumber(BigInt(0), 8 - orderIndexes.length, true)
    })

    paramStream.addUint(BigInt(0))
    paramStream.addHex(data.getData)

    return paramStream.getData
  }

  private def setupOrderDefaults(order: XRawOrder) = {
    val defaultAddr = "0x0"
    val fullZeroAddr = "0x" + "0" * 40
    val defaultUint256 = ByteString.copyFromUtf8("0")

    val addressGetOrDefault = (addr: String) => if (isValidAddress(addr)) addr else defaultAddr

    val uint256GetOrDefault = (uint256Bs: ByteString) => {
      if (uint256Bs.isEmpty) defaultUint256 else uint256Bs
    }

    var params = order.params.getOrElse(new XRawOrder.Params)
    var feeParams = order.feeParams.getOrElse(new XRawOrder.FeeParams)
    var erc1400Params = order.erc1400Params.getOrElse(new XRawOrder.ERC1400Params)

    params = params.copy(
      dualAuthAddr = addressGetOrDefault(params.dualAuthAddr),
      broker = addressGetOrDefault(params.broker),
      orderInterceptor = addressGetOrDefault(params.orderInterceptor),
      wallet = addressGetOrDefault(params.wallet)
    )

    feeParams = feeParams.copy(
      amountFee = uint256GetOrDefault(feeParams.amountFee),
      tokenRecipient = addressGetOrDefault(feeParams.tokenRecipient)
    )

    if (feeParams.tokenFee.length == 0
      || feeParams.tokenFee == defaultAddr
      || feeParams.tokenFee == fullZeroAddr) {
      feeParams = feeParams.copy(tokenFee = context.lrcAddress)
    }

    order.copy(
      params = Option(params),
      feeParams = Option(feeParams),
      erc1400Params = Option(erc1400Params),
    )
  }

  private def addDataAndOffset(
    data: Any,
    solidityType: String,
    dataBits: Bitstream,
    tablesBits: Bitstream,
    dataForceAppend: Boolean
  ) {
    val offset = solidityType match {
      case "uint8" ⇒
        val dataInt = data.asInstanceOf[Int]
        dataBits.addNumber(BigInt(dataInt), 1, dataForceAppend)
      case "uint16" ⇒
        val dataInt = data.asInstanceOf[Int]
        dataBits.addNumber(BigInt(dataInt), 2, dataForceAppend)
      case "uint32" ⇒
        val dataInt = data.asInstanceOf[Int]
        dataBits.addNumber(BigInt(dataInt), 4, dataForceAppend)
      case "uint" | "uint256" ⇒
        val dataStr = data.asInstanceOf[String]
        dataBits.addUint(dataStr, dataForceAppend)
      case "address" ⇒
        val dataStr = data.asInstanceOf[String]
        dataBits.addAddress(dataStr, dataForceAppend)
      case "bytes32" ⇒
        val dataStr = data.asInstanceOf[String]
        dataBits.addHex(dataStr, dataForceAppend)
      case "bytes" ⇒
        val dataStr = data.asInstanceOf[String]
        dataBits.addHex(dataStr, dataForceAppend)
      case _ ⇒
        throw new IllegalArgumentException(s"unsupported solidity type: $solidityType")
    }

    insertOffset(tablesBits, offset)
  }

  private def setupMiningInfo(xRingBatch: XRingBatch, data: Bitstream, tables: Bitstream) {
    val feeRecipient = if (isValidAddress(xRingBatch.feeRecipient)) xRingBatch.feeRecipient else xRingBatch.transactionOrigin
    val miner = if (isValidAddress(xRingBatch.miner)) xRingBatch.miner else feeRecipient
    if (feeRecipient != xRingBatch.transactionOrigin) {
      insertOffset(tables, data.addAddress(feeRecipient, false))
    } else {
      insertDefault(tables)
    }

    if (miner != feeRecipient) {
      insertOffset(tables, data.addAddress(miner, false))
    } else {
      insertDefault(tables)
    }

    if (xRingBatch.sig != null && xRingBatch.sig.length > 0
      && miner != xRingBatch.transactionOrigin) {
      insertOffset(tables, data.addHex(xRingBatch.sig, false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }
  }

  private def setupOrderInfo(data: Bitstream, tables: Bitstream,
    order: XRawOrder, tokenSpendables: Map[String, Int]) {
    addPadding(data)
    insertOffset(tables, OrderVersion)
    insertOffset(tables, data.addAddress(order.owner, false))
    insertOffset(tables, data.addAddress(order.tokenS, false))
    insertOffset(tables, data.addAddress(order.tokenB, false))
    insertOffset(tables, data.addUint(order.amountS.toStringUtf8, false))
    insertOffset(tables, data.addUint(order.amountB.toStringUtf8, false))
    insertOffset(tables, data.addUint32(order.validSince, false))

    val spendableSIndex = tokenSpendables(order.owner + order.tokenS)
    val spendableFeeIndex = tokenSpendables(order.owner + order.feeParams.get.tokenFee)
    tables.addUint16(spendableSIndex)
    tables.addUint16(spendableFeeIndex)

    if (isValidAddress(order.params.get.dualAuthAddr)) {
      insertOffset(tables, data.addAddress(order.params.get.dualAuthAddr, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(order.params.get.broker)) {
      insertOffset(tables, data.addAddress(order.params.get.broker, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(order.params.get.orderInterceptor)) {
      insertOffset(tables, data.addAddress(order.params.get.orderInterceptor, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(order.params.get.wallet)) {
      insertOffset(tables, data.addAddress(order.params.get.wallet, false))
    } else {
      insertDefault(tables)
    }

    if (order.params.get.validUntil > 0) {
      insertOffset(tables, data.addUint32(order.params.get.validUntil, false))
    } else {
      insertDefault(tables)
    }

    val orderSig = order.params.get.sig
    if (orderSig != null && orderSig.length > 0) {
      insertOffset(tables, data.addHex(orderSig, false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }

    val dualAuthSig = order.params.get.dualAuthSig
    if (dualAuthSig != null && dualAuthSig.length > 0) {
      insertOffset(tables, data.addHex(dualAuthSig, false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }

    val allOrNoneInt = if (order.params.get.allOrNone) 1 else 0
    tables.addUint16(allOrNoneInt)

    val tokenFee = order.feeParams.get.tokenFee
    if (tokenFee.length > 0 && tokenFee != context.lrcAddress) {
      insertOffset(tables, data.addAddress(tokenFee, false))
    } else {
      insertDefault(tables)
    }

    val amountFee = BigInt(order.feeParams.get.amountFee.toStringUtf8, 16)
    if (amountFee > 0) {
      insertOffset(tables, data.addUint(amountFee, false))
    } else {
      insertDefault(tables)
    }

    tables.addUint16(order.feeParams.get.waiveFeePercentage)
    tables.addUint16(order.feeParams.get.tokenSFeePercentage)
    tables.addUint16(order.feeParams.get.tokenBFeePercentage)

    val tokenRecipient = order.feeParams.get.tokenRecipient
    if (tokenRecipient.length > 0 && tokenRecipient != order.owner) {
      insertOffset(tables, data.addAddress(tokenRecipient, false))
    } else {
      insertDefault(tables)
    }

    tables.addUint16(order.feeParams.get.walletSplitPercentage)
  }

  private def insertOffset(tables: Bitstream, offset: Int) {
    assert(offset % 4 == 0)
    tables.addUint16(offset / 4)
  }

  private def insertDefault(tables: Bitstream) = tables.addUint16(0)

  private def addPadding(data: Bitstream) {
    val paddingLength = data.length % 4
    if (paddingLength > 0) {
      data.addNumber(BigInt(0), 4 - paddingLength)
    }
  }

  private def ringBatchHash(xRingBatch: XRingBatch) = {
    val ringHashes = xRingBatch.rings.map(
      xring ⇒ {
        val bitstream = new Bitstream
        val orders = xring.orderIndexes.map(i ⇒ xRingBatch.orders(i))
        orders.foreach(o ⇒ {
          bitstream.addHex(o.hash)
          bitstream.addUint16(o.feeParams.get.waiveFeePercentage)
        })
        Numeric.toHexString(Hash.sha3(bitstream.getBytes))
      }
    )

    val ringHashesXor = ringHashes.tail.foldLeft(ringHashes.head) {
      (h1: String, h2: String) ⇒
        {
          val bigInt1 = Numeric.decodeQuantity(h1)
          val bigInt2 = Numeric.decodeQuantity(h2)
          val bigIntXor = bigInt1.xor(bigInt2)
          Numeric.encodeQuantity(bigIntXor)
        }
    }

    var feeRecipient = xRingBatch.feeRecipient
    if (feeRecipient == null || feeRecipient.length == 0) {
      feeRecipient = xRingBatch.transactionOrigin
    }
    var miner = xRingBatch.miner
    if (miner == null || miner.length == 0 || miner.equalsIgnoreCase(feeRecipient)) {
      miner = "0x0"
    }

    val ringBatchBits = new Bitstream
    ringBatchBits.addAddress(feeRecipient, true)
    ringBatchBits.addAddress(miner, true)
    ringBatchBits.addHex(ringHashesXor)

    Numeric.toHexString(Hash.sha3(ringBatchBits.getBytes))
  }

  private def sign(xRingBatch: XRingBatch) = {
    val hash = ringBatchHash(xRingBatch)
    val credentials = Credentials.create(context.minerPrivateKey)
    val sigData = Sign.signMessage(
      Numeric.hexStringToByteArray(hash),
      credentials.getEcKeyPair
    )

    val sigBytes = sigData.getR ++ sigData.getS
    val sig = Numeric.toHexString(sigBytes)

    xRingBatch.copy(hash = hash, sig = sig)
  }

}
