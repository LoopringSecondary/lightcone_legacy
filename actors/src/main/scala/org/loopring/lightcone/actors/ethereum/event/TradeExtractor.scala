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

package org.loopring.lightcone.actors.ethereum.event

import javax.inject.Inject
import org.loopring.lightcone.core.base.{MarketKey, MetadataManager}
import org.loopring.lightcone.ethereum.SimpleRingBatchDeserializer
import org.loopring.lightcone.ethereum.abi.{
  ringSubmitterAbi,
  SubmitRingsFunction
}
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent.{ExecutionContext, Future}

class TradeExtractor @Inject()(
    implicit
    extractor: RingMinedEventExtractor,
    metadataManager: MetadataManager,
    val ec: ExecutionContext)
    extends EventExtractor[PersistTrades.Req] {

  implicit val ringBatchContext = RingBatchContext(
    lrcAddress = metadataManager.getTokenBySymbol("lrc").get.meta.address
  )

  def extract(block: RawBlockData): Future[Seq[PersistTrades.Req]] = {
    for {
      rings <- extractor.extract(block).map { rings =>
        rings.filter(
          ring =>
            ring.header.isDefined && ring.header.get.txStatus.isTxStatusSuccess
        )
      }
      ringBatches = extractOrders(block)
      trades = extractTrades(rings, ringBatches)
    } yield Seq(PersistTrades.Req(trades))
  }

  private def extractOrders(block: RawBlockData): Map[String, RingBatch] = {
    block.txs
      .map(
        tx =>
          ringSubmitterAbi.unpackFunctionInput(tx.input) match {
            case Some(params: SubmitRingsFunction.Params) =>
              val ringData = params.data
              new SimpleRingBatchDeserializer(Numeric.toHexString(ringData)).deserialize match {
                case Left(_) =>
                  None
                case Right(ringBatch) =>
                  Some(tx.hash -> ringBatch)
              }
            case _ =>
              None
          }
      )
      .filter(_.isDefined)
      .map(_.get)
      .toMap
  }

  private def extractTrades(
      rings: Seq[RingMinedEvent],
      ringBatches: Map[String, RingBatch]
    ): Seq[Trade] = {
    rings.flatMap { ring =>
      ring.fills.zipWithIndex.map {
        case (fill, index) =>
          val _fill =
            if (index + 1 >= ring.fills.size) ring.fills.head
            else ring.fills(index + 1)
          val marketKey = MarketKey(fill.tokenS, _fill.tokenS).toString
          val ringBatch = ringBatches(fill.getHeader.txHash)
          val order = ringBatch.orders
            .find(order => fill.orderHash.equalsIgnoreCase(order.hash))
            .get
          Trade(
            owner = Address.normalize(fill.owner),
            orderHash = fill.orderHash,
            ringHash = fill.ringHash,
            ringIndex = fill.ringIndex,
            fillIndex = index,
            txHash = fill.header.get.txHash,
            amountS = fill.filledAmountS,
            amountB = _fill.filledAmountS,
            tokenS = Address.normalize(fill.tokenS),
            tokenB = Address.normalize(_fill.tokenS),
            marketKey = marketKey,
            split = fill.split,
            fee = Some(
              Trade.Fee(
                tokenFee = Address.normalize(order.feeParams.get.tokenFee),
                amountFee = fill.filledAmountFee,
                feeAmountS = fill.feeAmountS,
                feeAmountB = fill.feeAmountB,
                feeRecipient = Address.normalize(fill.feeRecipient),
                waiveFeePercentage = order.getFeeParams.waiveFeePercentage,
                walletSplitPercentage = order.getFeeParams.walletSplitPercentage
              )
            ),
            wallet = Address.normalize(order.getParams.wallet),
            miner = Address.normalize(ringBatch.miner),
            blockHeight = fill.header.get.blockNumber,
            blockTimestamp = fill.header.get.blockTimestamp
          )
      }
    }
  }
}
