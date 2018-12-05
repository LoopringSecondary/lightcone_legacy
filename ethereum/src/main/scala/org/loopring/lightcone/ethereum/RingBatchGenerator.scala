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
import org.loopring.lightcone.proto.core._

trait RingBatchGenerator {
  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch
  def toSubmitableParamStr(xRingBatch: XRingBatch): String
}

// TODO(kongliang): implement and test this class
class RingBatchGeneratorImpl(context: XRingBatchContext)
  extends RingBatchGenerator {

  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch = {
    val orderValidator = new RawOrderValidatorImpl

    val ordersDistinctedMap = orders
      .flatten
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
    ""
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
        Numeric.toHexString(Hash.sha3(bitstream.getPackedBytes))
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

    Numeric.toHexString(Hash.sha3(ringBatchBits.getPackedBytes))
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
