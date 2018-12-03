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

import org.web3j.utils.Numeric
import org.loopring.lightcone.proto.core._

trait RingBatchGenerator {
  def generateAndSignRingBatch(orders: Seq[Seq[XRawOrder]]): XRingBatch
}

// TODO(kongliang): implement and test this class
class RingBatchGeneratorImpl(context: XRingBatchContext)
  extends RingBatchGenerator {

  private def sign(xRingBatch: XRingBatch) = {
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
      .toSeq

    val ordersHashIndexMap = ordersDistinctedSeq
      .map(_.hash)
      .zipWithIndex
      .toMap

    val xrings = orders.map(orders ⇒ {
      val orderIndexes = orders.map(order ⇒ ordersHashIndexMap(order.hash))
      new XRingBatch.XRing(orderIndexes)
    })

    val xringBatch = new XRingBatch()
      .withFeeRecipient(context.feeRecipient)
      .withMiner(context.miner)
      .withRings(xrings)
      .withOrders(ordersDistinctedSeq)
      .withSignAlgorithm(XSigningAlgorithm.ALGO_ETHEREUM)
      .withTransactionOrigin(context.transactionOrigin)

    sign(xringBatch)
  }
}
