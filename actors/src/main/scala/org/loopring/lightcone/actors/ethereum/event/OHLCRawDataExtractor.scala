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

import com.google.inject.Inject
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.ethereum.data._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

import scala.concurrent._

class OHLCRawDataExtractor @Inject()(
    implicit
    extractor: RingMinedEventExtractor,
    val ec: ExecutionContext,
    val metadataManager: MetadataManager)
    extends EventExtractor[OHLCRawData] {

  def extract(block: RawBlockData): Future[Seq[OHLCRawData]] = {
    extractor
      .extract(block)
      .map { rings =>
        rings.filter(
          ring =>
            ring.header.isDefined && ring.header.get.txStatus.isTxStatusSuccess
        )
      }
      .map { rings =>
        rings.flatMap { ring =>
          ring.fills.map { fill =>
            val marketHash =
              MarketHash(MarketPair(fill.tokenS, fill.tokenB)).toString

            if (!metadataManager.isMarketActiveOrReadOnly(marketHash)) None
            else {
              val marketMetadata =
                metadataManager.getMarketMetadata(marketHash)
              val marketPair = marketMetadata.getMarketPair
              val baseToken =
                metadataManager.getToken(marketPair.baseToken).get
              val quoteToken =
                metadataManager.getToken(marketPair.quoteToken).get
              val (baseAmount, quoteAmount) =
                getAmounts(fill, baseToken, quoteToken, marketMetadata)
              Some(
                OHLCRawData(
                  ringIndex = ring.ringIndex,
                  txHash = ring.header.get.txHash,
                  marketHash = marketHash,
                  time = ring.header.get.blockTimestamp,
                  baseAmount = baseAmount,
                  quoteAmount = quoteAmount,
                  price = BigDecimal(quoteAmount / baseAmount)
                    .setScale(marketMetadata.priceDecimals)
                    .doubleValue()
                )
              )
            }
          }.filter(_.isDefined).map(_.get).distinct
        }
      }
  }

  // LRC-WETH market, LRC is the base token, WETH is the quote token.
  def getAmounts(
      fill: OrderFilledEvent,
      baseToken: Token,
      quoteToken: Token,
      marketMetadata: MarketMetadata
    ): (Double, Double) = {
    val amountInWei =
      if (Address(baseToken.meta.address).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(fill.filledAmountB.toByteArray)

    val amount: Double = quoteToken
      .fromWei(amountInWei, marketMetadata.precisionForAmount)
      .doubleValue()

    val totalInWei =
      if (Address(quoteToken.meta.address).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(fill.filledAmountB.toByteArray)

    val total: Double = baseToken
      .fromWei(totalInWei, marketMetadata.precisionForTotal)
      .doubleValue()

    amount -> total
  }
}
