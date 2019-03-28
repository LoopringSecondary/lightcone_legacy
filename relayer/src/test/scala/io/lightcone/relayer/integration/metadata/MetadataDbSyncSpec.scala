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

package io.lightcone.relayer.integration

import io.lightcone.core.ErrorCode.ERR_INVALID_MARKET
import io.lightcone.core.MarketMetadata.Status.TERMINATED
import io.lightcone.core._
import io.lightcone.relayer.data._
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration.AddedMatchers._
import org.scalatest._

import scala.concurrent.{Await, Future}

/**
  * 测试修改数据库后，可以通过定时更新到token和market的状态
  */
class MetadataDbSyncSpec
    extends FeatureSpec
    with GivenWhenThen
    with CommonHelper
    with MetadataSpecHelper
    with CancelHelper
    with ValidateHelper
    with Matchers {

  feature("test sync from db") {
    scenario("synced metadatas from db after refresh interval") {

      val newBurnRate = BurnRate(0.2, 0.2)
      addBatchBurnRateExpects(
        {
          case batchReq: BatchGetBurnRate.Req =>
            BatchGetBurnRate.Res(
              resps = batchReq.reqs.map { req =>
                GetBurnRate.Res(
                  burnRate = Some(newBurnRate),
                  block = 110
                )
              }
            )
        }
      )
      val getTokensReq = GetTokens
        .Req(
          true,
          true,
          true,
          "USD",
          Seq(GTO_TOKEN.address)
        )
      val getTokensInitRes = getTokensReq
        .expect(check((res: GetTokens.Res) => res.tokens.nonEmpty))

      val ts = timeProvider.getTimeSeconds()
      val tickers = externalTickers.map { record =>
        if (record.symbol == GTO_TOKEN.symbol) {
          record.copy(price = 0.04, timestamp = ts)
        } else record.copy(timestamp = ts)
      }
      val saveF = Future.sequence {
        Seq(
          dbModule.tokenMetadataDal.updateTokenMetadata(
            GTO_TOKEN.copy(burnRate = Some(newBurnRate))
          ),
          dbModule.tokenInfoDal.updateTokenInfo(
            TokenInfo(symbol = GTO_TOKEN.symbol, circulatingSupply = 100000000)
          ),
          dbModule.tokenTickerRecordDal.saveTickers(tickers),
          dbModule.marketMetadataDal.updateMarket(
            GTO_WETH_MARKET.copy(status = TERMINATED)
          )
        )
      }
      Await.result(saveF, timeout.duration)
      Await.result(dbModule.tokenTickerRecordDal.setValid(ts), timeout.duration)

      Thread.sleep((metadataRefresherInterval + 5) * 1000)
      val getTokensRes = getTokensReq
        .expect(check((res: GetTokens.Res) => res.tokens.nonEmpty))

      val gtoTokenRes = getTokensRes.tokens(0)
      gtoTokenRes.getTicker.price should be(0.04)
      gtoTokenRes.getMetadata.getBurnRate should be(newBurnRate)
      gtoTokenRes.getInfo.circulatingSupply should be(100000000)

      val getOrderbookReq = GetOrderbook.Req(
        size = 10,
        marketPair = GTO_WETH_MARKET.marketPair
      )

      val getOrderbookRes =
        getOrderbookReq.expect(check((err: ErrorException) => true))

      getOrderbookRes.error.code should be(ERR_INVALID_MARKET)
      getOrderbookRes.getMessage() should include("market status is not one of")
    }
  }
}
