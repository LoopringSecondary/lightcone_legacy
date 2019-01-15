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

package org.loopring.lightcone.actors.metadata

import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern._
import akka.testkit.TestProbe
import akka.util.Timeout
import org.loopring.lightcone.actors.core.MetadataManagerActor
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.actors.validator.MetadataManagerValidator
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MetadataManagerSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with EthereumSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport
    with MetadataManagerSupport {

  val probe = TestProbe()
  val mediator = DistributedPubSub(system).mediator
  mediator ! Subscribe(MetadataManagerActor.tokenChangedTopicId, probe.ref)
  mediator ! Subscribe(MetadataManagerActor.marketChangedTopicId, probe.ref)
  val actor = actors.get(MetadataManagerValidator.name)

  "load tokens config" must {
    "get all tokens config" in {
      info("save some tokens config")
      val lrc = TokenMetadata(
        `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
        status = TokenMetadata.Status.ENABLED,
        symbol = "LRC",
        name = "Loopring Token",
        address = "0x1c1b9d3819ab7a3da0353fe0f9e41d3f89192cf8",
        unit = "LRC",
        decimals = 18,
        precision = 6,
        burnRate = 0.1,
        usdPrice = 10
      )
      val tokens = Seq(
        lrc,
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "ABC",
          name = "ABC Token",
          address = "0x255Aa6DF07540Cb5d3d297f0D0D4D84cb52bc8e6",
          unit = "ABC",
          decimals = 18,
          precision = 6,
          burnRate = 0.2,
          usdPrice = 8
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "BCD",
          name = "BCD Token",
          address = "0x989fcbc46845a290e971a6303ef3753fb039d8d5",
          unit = "BCD",
          decimals = 9,
          precision = 3,
          burnRate = 1.1,
          usdPrice = 1
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "BNB",
          name = "Binance Token",
          address = "0x61a11f3d1f3b4dbd3f780f004773e620daf065c4",
          unit = "BNB",
          decimals = 18,
          precision = 6,
          burnRate = 0.2,
          usdPrice = 8
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "0x",
          name = "ZeroX Token",
          address = "0x34a381433f45230390d750113aab46c65129ffab",
          unit = "0x",
          decimals = 18,
          precision = 6,
          burnRate = 0.3,
          usdPrice = 7
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.DISABLED,
          symbol = "CDE",
          name = "CDE Token",
          address = "0xfdeda15e2922c5ed41fc1fdf36da2fb2623666b3",
          unit = "CDE",
          decimals = 6,
          precision = 3,
          burnRate = 1.1,
          usdPrice = 1
        )
      )
      actor ! SaveTokenMetadatas.Req(tokens)
      Thread.sleep(3000)

      info("query the tokens")
      val r1 = dbModule.tokenMetadataService.getTokens(tokens.map(_.address))
      val res1 = Await.result(r1.mapTo[Seq[TokenMetadata]], 5.second)
      assert(res1.length == tokens.length)

      info("save a new token config: DEF")
      val r2 = dbModule.tokenMetadataService.saveToken(
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.DISABLED,
          symbol = "DEF",
          name = "DEF Token",
          address = "0x244929a8141d2134d9323e65309fb46e4a983840",
          unit = "DEF",
          decimals = 6,
          precision = 3,
          burnRate = 1.1,
          usdPrice = 1
        )
      )
      val res2 = Await.result(r2.mapTo[ErrorCode], 5.second)
      assert(res2 == ErrorCode.ERR_NONE)

      info("update LRC token config")
      val r3 = dbModule.tokenMetadataService.updateToken(
        lrc.copy(burnRate = 0.2, usdPrice = 20)
      )
      val res3 = Await.result(r3.mapTo[ErrorCode], 5.second)
      assert(res3 == ErrorCode.ERR_NONE)
      Thread.sleep(3000)

      info("subscriber should received the message")
      probe.expectMsg(MetadataChanged(MetadataChanged.Changed.Tokens(true)))
    }
  }

  "load markets config" must {
    "get all markets config" in {
      info("save some markets config")
      val LRC = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val BNB = "0x9b9211a2ce4eEE9c5619d54E5CD9f967A68FBE23"
      val ZRX = "0x7b22713f2e818fad945af5a3618a2814f102cbe0"
      val WETH = "0x45245bc59219eeaaf6cd3f382e078a461ff9de7b"
      val marketIdLrcWeth = MarketId(primary = WETH, secondary = LRC)
      val marketIdBnbWeth = MarketId(primary = WETH, secondary = BNB)
      val marketIdZrxdWeth = MarketId(primary = WETH, secondary = ZRX)
      val marketLrcWeth = MarketMetadata(
        status = MarketMetadata.Status.ENABLED,
        secondaryTokenSymbol = "LRC",
        primaryTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdLrcWeth),
        marketHash = MarketHashProvider.convert2Hex(WETH, LRC)
      )
      val markets = Seq(
        marketLrcWeth,
        MarketMetadata(
          status = MarketMetadata.Status.DISABLED,
          secondaryTokenSymbol = "BNB",
          primaryTokenSymbol = "WETH",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketId = Some(marketIdBnbWeth),
          marketHash = MarketHashProvider.convert2Hex(WETH, BNB)
        ),
        MarketMetadata(
          status = MarketMetadata.Status.READONLY,
          secondaryTokenSymbol = "ZRX",
          primaryTokenSymbol = "WETH",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketId = Some(marketIdZrxdWeth),
          marketHash = MarketHashProvider.convert2Hex(WETH, ZRX)
        )
      )
      actor ! SaveMarketMetadatas.Req(markets)
      Thread.sleep(3000)

      info("query the markets")
      val r1 = dbModule.marketMetadataService.getMarketsByHash(
        markets.map(_.marketHash)
      )
      val res1 = Await.result(r1.mapTo[Seq[MarketMetadata]], 5.second)
      assert(res1.length == markets.length)

      info("save a new market: ABC-LRC")
      val ABC = "0x244929a8141d2134d9323e65309fb46e4a983840"
      val marketIdAbcLrc = MarketId(primary = ABC, secondary = LRC)
      val abcLrc = MarketMetadata(
        status = MarketMetadata.Status.READONLY,
        secondaryTokenSymbol = "ABC",
        primaryTokenSymbol = "LRC",
        maxNumbersOfOrders = 1000,
        priceDecimals = 3,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 6,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdAbcLrc),
        marketHash = MarketHashProvider.convert2Hex(ABC, LRC)
      )
      val r2 = dbModule.marketMetadataService.saveMarket(abcLrc)
      val res2 = Await.result(r2.mapTo[ErrorCode], 5.second)
      assert(res2 == ErrorCode.ERR_NONE)

      info("update Lrc-Weth market config")
      val r3 = dbModule.marketMetadataService.updateMarket(
        marketLrcWeth
          .copy(priceDecimals = 2, status = MarketMetadata.Status.READONLY)
      )
      val res3 = Await.result(r3.mapTo[ErrorCode], 5.second)
      assert(res3 == ErrorCode.ERR_NONE)
      Thread.sleep(3000)

      info("subscriber should received the message")
      probe.expectMsg(MetadataChanged(MetadataChanged.Changed.Markets(true)))
    }
  }

}
