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
import io.lightcone.relayer.integration.integrationStarter

import scala.concurrent._

trait MetadataHelper extends DbHelper {

  def prepareMetadata(
      tokens: Seq[Token],
      markets: Seq[Market],
      symbolSlugs: Seq[CMCCrawlerConfigForToken]
    )(
      implicit
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      timeout: Timeout,
      timeProvider: TimeProvider
    ) = {

    val externalTickerRecords = tokens.map { token =>
      TokenTickerRecord(
        symbol = token.getMetadata.symbol,
        slug = token.getMetadata.name,
        price = token.getTicker.price,
        isValid = true,
        timestamp = 10,
        dataSource = "Dynamic"
      )
    }

    dbModule.tokenMetadataDal.saveTokenMetadatas(tokens.map(_.getMetadata))
    dbModule.tokenInfoDal.saveTokenInfos(tokens.map(_.getInfo))
    dbModule.cmcCrawlerConfigForTokenDal.saveConfigs(symbolSlugs)
    dbModule.marketMetadataDal.saveMarkets(markets.map(_.getMetadata))

    metadataManager.reset(
      metadataManager.getTokens() ++ tokens,
      metadataManager.getMarkets() ++ markets
    )
    Await.result(
      dbModule.tokenTickerRecordDal.saveTickers(externalTickerRecords),
      timeout.duration
    )
    Await.result(
      dbModule.tokenTickerRecordDal.setValid(10),
      timeout.duration
    )
  }

  def createAndSaveNewMarket(
      price1: Double = 1.0,
      price2: Double = 1.0,
      status: MarketMetadata.Status = MarketMetadata.Status.ACTIVE
    )(
      implicit
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      timeout: Timeout,
      timeProvider: TimeProvider,
      ec: ExecutionContext
    ): Seq[Token] = {
    val tokens =
      Seq(createNewToken(price = price1), createNewToken(price = price2))
    val marketPair =
      MarketPair(tokens(0).getMetadata.address, tokens(1).getMetadata.address)
    val marketMetadata = MarketMetadata(
      status = status,
      baseTokenSymbol = tokens(0).getMetadata.symbol,
      quoteTokenSymbol = tokens(1).getMetadata.symbol,
      maxNumbersOfOrders = 1000,
      priceDecimals = 6,
      orderbookAggLevels = 6,
      precisionForAmount = 5,
      precisionForTotal = 5,
      browsableInWallet = true,
      marketPair = Some(marketPair),
      marketHash = marketPair.hashString
    )
    val market = Market(
      Some(marketMetadata),
      Some(
        MarketTicker(
          baseToken = marketMetadata.marketPair.get.baseToken,
          quoteToken = marketMetadata.marketPair.get.quoteToken,
          price = tokens(0).getTicker.price / tokens(1).getTicker.price
        )
      )
    )
    val symbolSlugs = Seq(
      CMCCrawlerConfigForToken(
        tokens(0).getMetadata.symbol,
        tokens(0).getMetadata.name
      ),
      CMCCrawlerConfigForToken(
        tokens(1).getMetadata.symbol,
        tokens(1).getMetadata.name
      )
    )
    prepareMetadata(tokens, Seq(market), symbolSlugs)
    integrationStarter.waiting()
    tokens
  }

  def createNewToken(
      address: String = getUniqueAccount().getAddress,
      decimals: Int = 18,
      burnRate: BurnRate = BurnRate(0.4, 0.5),
      status: TokenMetadata.Status = TokenMetadata.Status.VALID,
      price: Double = 1.0
    ): Token = {
    val meta = TokenMetadata(
      address = address,
      decimals = decimals,
      burnRate = Some(burnRate),
      symbol = s"d-${getUniqueInt()}",
      name = s"dynamic-${getUniqueInt()}",
      status = status
    )
    Token(
      Some(meta),
      Some(TokenInfo(symbol = meta.symbol)),
      Some(TokenTicker(token = meta.address, price = price))
    )
  }

}
