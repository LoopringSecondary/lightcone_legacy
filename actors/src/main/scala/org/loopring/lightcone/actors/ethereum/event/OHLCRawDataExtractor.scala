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
          ring.fills.zipWithIndex.map {
            case (fill, index) =>
              val _fill =
                if (index + 1 >= ring.fills.size) ring.fills.head
                else ring.fills(index + 1)

              val marketKey = MarketKey(fill.tokenS, _fill.tokenS).toString

              if (!metadataManager.isValidMarket(marketKey)) None
              else {
                val marketMetadata =
                  metadataManager.getMarketMetadata(marketKey).get

                val marketId = marketMetadata.getMarketId

                val primaryToken =
                  metadataManager.getToken(marketId.primary).get
                val secondToken =
                  metadataManager.getToken(marketId.secondary).get

                val (quality, amount) = getAmounts(
                  fill,
                  _fill,
                  primaryToken,
                  secondToken,
                  marketMetadata
                )

                Some(
                  OHLCRawData(
                    ringIndex = ring.ringIndex,
                    txHash = ring.header.get.txHash,
                    marketKey = marketKey,
                    time = ring.header.get.blockTimestamp,
                    quality = quality,
                    amount = amount,
                    price = BigDecimal(amount / quality)
                      .setScale(marketMetadata.priceDecimals)
                      .doubleValue()
                  )
                )
              }
          }.filter(_.isDefined).map(_.get).distinct
        }
      }
  }

  def getAmounts(
      fill: OrderFilledEvent,
      _fill: OrderFilledEvent,
      primaryToken: Token,
      secondToken: Token,
      marketMetadata: MarketMetadata
    ): (Double, Double) = {
    val amountInWei =
      if (Address(primaryToken.meta.address).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(_fill.filledAmountS.toByteArray)

    val amount: Double = secondToken
      .fromWei(amountInWei, marketMetadata.precisionForTotal)
      .doubleValue()

    val qualityInWei =
      if (Address(secondToken.meta.address).equals(Address(fill.tokenS)))
        Numeric.toBigInt(fill.filledAmountS.toByteArray)
      else Numeric.toBigInt(_fill.filledAmountS.toByteArray)

    val quality: Double = primaryToken
      .fromWei(qualityInWei, marketMetadata.precisionForAmount)
      .doubleValue()

    quality -> amount
  }
}
