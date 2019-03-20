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

package io.lightcone.relayer.integration.helper
import akka.util.Timeout
import io.lightcone.core._
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence._
import io.lightcone.relayer.data._
import io.lightcone.relayer._

import scala.concurrent.Await

trait MetadataHelper extends DbHelper {

  def prepareMetadata(
      tokenMetadatas: Seq[TokenMetadata],
      marketMetadatas: Seq[MarketMetadata],
      externalTickerRecords: Seq[TokenTickerRecord]
    )(
      implicit
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      timeout: Timeout,
      timeProvider: TimeProvider
    ) = {

    dbModule.tokenMetadataDal.saveTokenMetadatas(tokenMetadatas)
    dbModule.tokenInfoDal.saveTokenInfos(tokenMetadatas.map { t =>
      TokenInfo(t.symbol)
    })
    dbModule.cmcCrawlerConfigForTokenDal.saveConfigs(
      externalTickerRecords.map(r => CMCCrawlerConfigForToken(r.symbol, r.slug))
    )
    dbModule.marketMetadataDal.saveMarkets(marketMetadatas)

    val tokens = tokenMetadatas.map { t =>
      Token(
        Some(t),
        Some(TokenInfo(symbol = t.symbol)),
        Some(TokenTicker(token = t.address, price = 0.1))
      )
    }

    val markets = marketMetadatas.map { m =>
      Market(
        Some(m),
        Some(
          MarketTicker(
            baseToken = m.marketPair.get.baseToken,
            quoteToken = m.marketPair.get.quoteToken,
            price = 0.0001
          )
        )
      )
    }
    metadataManager.reset(
      tokens,
      markets
    )
    Await.result(
      dbModule.tokenTickerRecordDal.saveTickers(externalTickerRecords),
      timeout.duration
    )
    Await.result(
      dbModule.tokenTickerRecordDal.setValid(timeProvider.getTimeSeconds()),
      timeout.duration
    )
  }

  def createAndSaveNewMarket(
      price: Double = 1.0
    )(
      implicit
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      timeout: Timeout,
      timeProvider: TimeProvider
    ): Unit = {
    val tokenMetadatas = Seq(createNewToken(), createNewToken())
    val marketPair =
      MarketPair(tokenMetadatas(0).address, tokenMetadatas(1).address)
    val marketMetadata = MarketMetadata(
      status = MarketMetadata.Status.ACTIVE,
      baseTokenSymbol = tokenMetadatas(0).symbol,
      quoteTokenSymbol = tokenMetadatas(1).symbol,
      maxNumbersOfOrders = 1000,
      priceDecimals = 6,
      orderbookAggLevels = 6,
      precisionForAmount = 5,
      precisionForTotal = 5,
      browsableInWallet = true,
      marketPair = Some(marketPair),
      marketHash = marketPair.hashString
    )
    val tickerRecords = tokenMetadatas.map { token =>
      TokenTickerRecord(
        symbol = token.symbol,
        slug = token.name,
        price = price,
        isValid = true,
        dataSource = "Dynamic"
      )
    }
    prepareMetadata(tokenMetadatas, Seq(marketMetadata), tickerRecords)
  }

  def createNewToken(
      address: String = getUniqueAccount().getAddress,
      decimals: Int = 18,
      burnRate: BurnRate = BurnRate(0.4, 0.5),
      status: TokenMetadata.Status = TokenMetadata.Status.VALID
    ): TokenMetadata = {
    TokenMetadata(
      address = address,
      decimals = decimals,
      burnRate = Some(burnRate),
      symbol = s"dynamic-${address.hashCode}",
      name = s"dynamic-${address}",
      status = status
    )
  }

}
