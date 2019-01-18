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

import org.web3j.crypto._
import org.web3j.utils.Numeric
import org.web3j.crypto.WalletUtils.isValidAddress
import com.google.protobuf.ByteString

import org.loopring.lightcone.proto._

trait RingBatchGenerator {

  def generateAndSignRingBatch(
      orders: Seq[Seq[RawOrder]]
    )(
      implicit
      context: RingBatchContext
    ): RingBatch

  def toSubmitableParamStr(
      xRingBatch: RingBatch
    )(
      implicit
      context: RingBatchContext
    ): String
}

object Protocol2RingBatchGenerator extends RingBatchGenerator {
  import ethereum._

  val OrderVersion = 0
  val SerializationVersion = 0

  def generateAndSignRingBatch(
      orders: Seq[Seq[RawOrder]]
    )(
      implicit
      context: RingBatchContext
    ): RingBatch = {
    val orderValidator = Protocol2RawOrderValidator

    val ordersWithHash = orders.map(
      ordersOfRing =>
        ordersOfRing.map(order => {
          val hash = orderValidator.calculateOrderHash(order)
          order.copy(hash = hash)
        })
    )

    val ordersDistinctedSeq = ordersWithHash.flatten
      .map(o => o.hash -> o)
      .toMap
      .map(_._2)
      .toSeq

    val ordersHashIndexMap = ordersDistinctedSeq
      .map(_.hash)
      .zipWithIndex
      .toMap

    val xrings = ordersWithHash.map(ordersOfRing => {
      val orderIndexes = ordersOfRing.map(o => ordersHashIndexMap(o.hash))
      new RingBatch.Ring(orderIndexes)
    })

    val xRingBatch = new RingBatch().copy(
      feeRecipient = context.feeRecipient,
      miner = context.miner,
      rings = xrings,
      orders = ordersDistinctedSeq,
      signAlgorithm = SigningAlgorithm.ALGO_ETHEREUM,
      transactionOrigin = context.transactionOrigin
    )

    sign(xRingBatch, context)
  }

  def toSubmitableParamStr(
      xRingBatch: RingBatch
    )(
      implicit
      context: RingBatchContext
    ): String = {
    val tokenSpendables = xRingBatch.orders.map { order =>
      Seq(
        (order.owner + order.tokenS),
        (order.owner + order.getFeeParams.tokenFee)
      )
    }.flatten.distinct.zipWithIndex.toMap

    val data = new Bitstream
    val tables = new Bitstream

    data.addUint(BigInt(0), true)
    setupMiningInfo(xRingBatch, data, tables)

    xRingBatch.orders.foreach(
      order => setupOrderInfo(data, tables, order, tokenSpendables, context)
    )

    val paramStream = new Bitstream
    paramStream.addUint16(SerializationVersion)
    paramStream.addUint16(xRingBatch.orders.length)
    paramStream.addUint16(xRingBatch.rings.length)
    paramStream.addUint16(tokenSpendables.size)
    paramStream.addHex(tables.getData)

    val ringIndexStream = new Bitstream
    xRingBatch.rings.foreach(ring => {
      val orderIndexes = ring.orderIndexes
      paramStream.addNumber(BigInt(orderIndexes.length), 1, true)
      orderIndexes.foreach(i => paramStream.addNumber(BigInt(i), 1, true))
      paramStream.addNumber(BigInt(0), 8 - orderIndexes.length, true)
    })

    paramStream.addUint(BigInt(0))
    paramStream.addHex(data.getData)

    return paramStream.getData
  }

  private def createBytes(data: String) = {
    val bitstream = new Bitstream
    bitstream.addUint((data.length - 2) / 2, true)
    bitstream.addHex(data)
    bitstream.getData
  }

  private def setupMiningInfo(
      xRingBatch: RingBatch,
      data: Bitstream,
      tables: Bitstream
    ) {
    val feeRecipient =
      if (isValidAddress(xRingBatch.feeRecipient)) xRingBatch.feeRecipient
      else xRingBatch.transactionOrigin
    val miner =
      if (isValidAddress(xRingBatch.miner)) xRingBatch.miner else feeRecipient
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
      insertOffset(tables, data.addHex(createBytes(xRingBatch.sig), false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }
  }

  private def setupOrderInfo(
      data: Bitstream,
      tables: Bitstream,
      order: RawOrder,
      tokenSpendables: Map[String, Int],
      context: RingBatchContext
    ) {
    val orderParams = order.getParams
    val orderFeeParams = order.getFeeParams
    val orderErc1400Params = order.getErc1400Params

    addPadding(data)
    insertOffset(tables, OrderVersion)
    insertOffset(tables, data.addAddress(order.owner, false))
    insertOffset(tables, data.addAddress(order.tokenS, false))
    insertOffset(tables, data.addAddress(order.tokenB, false))
    insertOffset(tables, data.addUint(order.amountS, false))
    insertOffset(tables, data.addUint(order.amountB, false))
    insertOffset(tables, data.addUint32(order.validSince, false))

    val spendableSIndex = tokenSpendables(order.owner + order.tokenS)
    val spendableFeeIndex = tokenSpendables(
      order.owner + orderFeeParams.tokenFee
    )
    tables.addUint16(spendableSIndex)
    tables.addUint16(spendableFeeIndex)

    if (isValidAddress(orderParams.dualAuthAddr)) {
      insertOffset(tables, data.addAddress(orderParams.dualAuthAddr, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(orderParams.broker)) {
      insertOffset(tables, data.addAddress(orderParams.broker, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(orderParams.orderInterceptor)) {
      insertOffset(tables, data.addAddress(orderParams.orderInterceptor, false))
    } else {
      insertDefault(tables)
    }

    if (isValidAddress(orderParams.wallet)) {
      insertOffset(tables, data.addAddress(orderParams.wallet, false))
    } else {
      insertDefault(tables)
    }

    if (orderParams.validUntil > 0) {
      insertOffset(tables, data.addUint32(orderParams.validUntil, false))
    } else {
      insertDefault(tables)
    }

    val orderSig = orderParams.sig
    if (orderSig != null && orderSig.length > 0) {
      insertOffset(tables, data.addHex(createBytes(orderSig), false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }

    val dualAuthSig = orderParams.dualAuthSig
    if (dualAuthSig != null && dualAuthSig.length > 0) {
      insertOffset(tables, data.addHex(createBytes(dualAuthSig), false))
      addPadding(data)
    } else {
      insertDefault(tables)
    }

    val allOrNoneInt = if (orderParams.allOrNone) 1 else 0
    tables.addUint16(allOrNoneInt)

    val tokenFee = orderFeeParams.tokenFee
    if (tokenFee.length > 0 && tokenFee != context.lrcAddress) {
      insertOffset(tables, data.addAddress(tokenFee, false))
    } else {
      insertDefault(tables)
    }

    if (orderFeeParams.amountFee > 0) {
      insertOffset(tables, data.addUint(orderFeeParams.amountFee, false))
    } else {
      insertDefault(tables)
    }

    tables.addInt16(orderFeeParams.waiveFeePercentage, true)
    tables.addUint16(orderFeeParams.tokenSFeePercentage, true)
    tables.addUint16(orderFeeParams.tokenBFeePercentage, true)

    val tokenRecipient = orderFeeParams.tokenRecipient
    if (tokenRecipient.length > 0 && tokenRecipient != order.owner) {
      insertOffset(tables, data.addAddress(tokenRecipient, false))
    } else {
      insertDefault(tables)
    }

    tables.addUint16(orderFeeParams.walletSplitPercentage, true)
    tables.addUint16(orderErc1400Params.tokenStandardS.value, true)
    tables.addUint16(orderErc1400Params.tokenStandardB.value, true)
    tables.addUint16(orderErc1400Params.tokenStandardFee.value, true)

    if (orderErc1400Params.trancheS.length > 0) {
      insertOffset(tables, data.addHex(orderErc1400Params.trancheS, false))
    } else {
      insertDefault(tables)
    }

    if (orderErc1400Params.trancheB.length > 0) {
      insertOffset(tables, data.addHex(orderErc1400Params.trancheB, false))
    } else {
      insertDefault(tables)
    }

    if (orderErc1400Params.transferDataS.length > 0) {
      insertOffset(
        tables,
        data.addHex(createBytes(orderErc1400Params.transferDataS), false)
      )
      addPadding(data)
    } else {
      insertDefault(tables)
    }
  }

  private def insertOffset(
      tables: Bitstream,
      offset: Int
    ) {
    assert(offset % 4 == 0)
    val slot = offset / 4
    tables.addUint16(slot)
  }

  private def insertDefault(tables: Bitstream) = tables.addUint16(0)

  private def addPadding(data: Bitstream) {
    val paddingLength = data.length % 4
    if (paddingLength > 0) {
      data.addNumber(BigInt(0), 4 - paddingLength)
    }
  }

  private def ringBatchHash(xRingBatch: RingBatch) = {
    val ringHashes = xRingBatch.rings.map(xring => {
      val bitstream = new Bitstream
      val orders = xring.orderIndexes.map(i => xRingBatch.orders(i))
      orders.foreach(o => {
        bitstream.addHex(o.hash)
        bitstream.addUint16(o.getFeeParams.waiveFeePercentage)
      })
      Numeric.toHexString(Hash.sha3(bitstream.getBytes))
    })

    val ringHashesXor = ringHashes.tail.foldLeft(ringHashes.head) {
      (h1: String, h2: String) =>
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
    if (miner == null || miner.length == 0 || miner.equalsIgnoreCase(
          feeRecipient
        )) {
      miner = "0x" + "0" * 40
    }

    val ringBatchBits = new Bitstream
    ringBatchBits.addAddress(feeRecipient, true)
    ringBatchBits.addAddress(miner, true)
    ringBatchBits.addBytes32(ringHashesXor, true)

    Numeric.toHexString(Hash.sha3(ringBatchBits.getBytes))
  }

  private def sign(
      xRingBatch: RingBatch,
      context: RingBatchContext
    ) = {
    val hash = ringBatchHash(xRingBatch)
    val sig = signPrefixedMessage(hash, context.minerPrivateKey)

    val ordersWithDualAuthSig = xRingBatch.orders.map(order => {
      val orderParams = order.getParams
      if (isAddressValidAndNonZero(orderParams.dualAuthAddr)) {
        val privateKey = orderParams.dualAuthPrivateKey
        val dualAuthSig = signPrefixedMessage(hash, privateKey)
        val newOrderParams = orderParams.copy(dualAuthSig = dualAuthSig)
        order.withParams(newOrderParams)
      } else {
        order
      }
    })

    xRingBatch.copy(hash = hash, sig = sig, orders = ordersWithDualAuthSig)
  }

  // For miner sig and dual-auth sigs, only ALGO_ETHEREUM algorithm supported for now.
  def signPrefixedMessage(
      hash: String,
      privateKey: String
    ) = {
    val credentials = Credentials.create(privateKey)
    val sigData = Sign.signPrefixedMessage(
      Numeric.hexStringToByteArray(hash),
      credentials.getEcKeyPair
    )

    val sigStream = new Bitstream
    sigStream.addNumber(SigningAlgorithm.ALGO_ETHEREUM.value, 1, true)
    sigStream.addNumber(1 + 32 + 32, 1, true)
    sigStream.addNumber(sigData.getV, 1, true)
    sigStream.addRawBytes(sigData.getR)
    sigStream.addRawBytes(sigData.getS)
    sigStream.getData
  }
}
