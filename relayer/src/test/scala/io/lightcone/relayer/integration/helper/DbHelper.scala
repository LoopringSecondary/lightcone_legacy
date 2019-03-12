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
import io.lightcone.relayer.integration.Metadatas
import org.slf4s.Logging

import scala.concurrent._

//数据库的prepare
trait DbHelper extends Logging {

  def prepareDbModule(dbModule: DatabaseModule) = {

    dbModule.tables.map { t =>
      t.deleteByFilter(_ => true)
    }
//    dbModule.createTables()
  }

  def prepareMetadata(
      dbModule: DatabaseModule,
      metadataManager: MetadataManager
    )(
      implicit
      timeout: Timeout,
      timeProvider: TimeProvider
    ) = {

    dbModule.tokenMetadataDal.saveTokenMetadatas(Metadatas.TOKENS)
    dbModule.tokenInfoDal.saveTokenInfos(Metadatas.TOKENS.map { t =>
      TokenInfo(t.symbol)
    })
    dbModule.cmcCrawlerConfigForTokenDal.saveConfigs(
      Metadatas.TOKEN_SLUGS_SYMBOLS.map { t =>
        CMCCrawlerConfigForToken(t._1, t._2)
      }
    )
    dbModule.marketMetadataDal.saveMarkets(Metadatas.MARKETS)

    val tokens = Metadatas.TOKENS.map { t =>
      Token(Some(t), Some(TokenInfo(symbol = t.symbol)), 0.1)
    }

    val markets = Metadatas.MARKETS.map { m =>
      Market(
        Some(m),
        Some(
          MarketTicker(
            baseTokenSymbol = m.baseTokenSymbol,
            quoteTokenSymbol = m.quoteTokenSymbol,
            price = 0.0001
          )
        )
      )
    }
    metadataManager.reset(
      tokens,
      markets
    )
    val tickers_ = Metadatas.externalTickers
    Await.result(
      dbModule.tokenTickerRecordDal.saveTickers(tickers_),
      timeout.duration
    )
    Await.result(
      dbModule.tokenTickerRecordDal.setValid(timeProvider.getTimeSeconds()),
      timeout.duration
    )

  }

}
