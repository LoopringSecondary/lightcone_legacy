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
import akka.testkit.TestProbe
import org.loopring.lightcone.actors.core.{
  EthereumQueryActor,
  MetadataManagerActor
}
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.actors.validator.MetadataManagerValidator
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.pattern._
import org.loopring.lightcone.lib.MarketHashProvider

class MetadataManagerSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport {

  val probe = TestProbe()
  val mediator = DistributedPubSub(system).mediator
  mediator ! Subscribe(MetadataManagerActor.pubsubTopic, probe.ref)
  val actor = actors.get(MetadataManagerValidator.name)
  val ethereumQueryActor = actors.get(EthereumQueryActor.name)

  "load tokens config" must {
    "get all tokens config" in {
      info("save some tokens config")
      val lrc = TokenMetadata(
        `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
        status = TokenMetadata.Status.ENABLED,
        symbol = "AAA",
        name = "AAA Token",
        address = "0x1c1b9d3819ab7a3da0353fe0f9e41d3f89192cf8",
        unit = "AAA",
        decimals = 18,
        precision = 6,
        burnRateForMarket = 0.1,
        burnRateForP2P = 0.2,
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
          burnRateForMarket = 0.3,
          burnRateForP2P = 0.4,
          usdPrice = 8
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "BBB",
          name = "BBB Token",
          address = "0x989fcbc46845a290e971a6303ef3753fb039d8d5",
          unit = "BBB",
          decimals = 9,
          precision = 3,
          burnRateForMarket = 0.1,
          burnRateForP2P = 0.3,
          usdPrice = 1
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "BBC",
          name = "BBC Token",
          address = "0x61a11f3d1f3b4dbd3f780f004773e620daf065c4",
          unit = "BBC",
          decimals = 18,
          precision = 6,
          burnRateForMarket = 0.2,
          burnRateForP2P = 0.1,
          usdPrice = 8
        ),
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.ENABLED,
          symbol = "CCC",
          name = "CCC Token",
          address = "0x34a381433f45230390d750113aab46c65129ffab",
          unit = "CCC",
          decimals = 18,
          precision = 6,
          burnRateForMarket = 0.6,
          burnRateForP2P = 0.8,
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
          burnRateForMarket = 0.1,
          burnRateForP2P = 0.9,
          usdPrice = 1
        )
      )

      val saved = Await.result(
        (actor ? SaveTokenMetadatas.Req(tokens)).mapTo[SaveTokenMetadatas.Res],
        5.second
      )
      assert(saved.savedAddresses.length == tokens.length)
      info("waiting 3s for repeatJob to reload tokens config")
      Thread.sleep(3000)

      info("subscriber should received the message")
      probe.expectMsg(MetadataChanged())

      info("query the tokens from db")
      val r1 = dbModule.tokenMetadataDal.getTokens(tokens.map(_.address))
      val res1 = Await.result(r1.mapTo[Seq[TokenMetadata]], 5.second)
      assert(res1.length == tokens.length)

      info("send a message to load tokens config")
      val q1 = Await.result(
        (actor ? LoadTokenMetadata.Req()).mapTo[LoadTokenMetadata.Res],
        5.second
      )
      assert(q1.tokens.length >= tokens.length)

      info("save a new token config: DEF")
      val r2 = dbModule.tokenMetadataDal.saveToken(
        TokenMetadata(
          `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
          status = TokenMetadata.Status.DISABLED,
          symbol = "DEF",
          name = "DEF Token",
          address = "0x244929a8141d2134d9323e65309fb46e4a983840",
          unit = "DEF",
          decimals = 6,
          precision = 3,
          burnRateForMarket = 0.5,
          burnRateForP2P = 0.5,
          usdPrice = 1
        )
      )
      val res2 = Await.result(r2.mapTo[ErrorCode], 5.second)
      assert(res2 == ErrorCode.ERR_NONE)

      val burnRateRes = Await.result(
        (ethereumQueryActor ? GetBurnRate.Req(
          token = lrc.address
        )).mapTo[GetBurnRate.Res],
        5.second
      )
      info(
        s"send a message to update burn-rate :{burnRate = 0.2, usdPrice = 20}, but will query ethereum and" +
          s" replace forMarket as :${burnRateRes.forMarket}, forP2P as :${burnRateRes.forP2P}"
      )
      val updated = Await.result(
        (actor ? UpdateTokenMetadata.Req(
          Some(lrc.copy(burnRateForMarket = 0.2, usdPrice = 20))
        )).mapTo[UpdateTokenMetadata.Res],
        5.second
      )
      assert(updated.error == ErrorCode.ERR_NONE)
      val query1 = Await.result(
        dbModule.tokenMetadataDal
          .getTokens(Seq(lrc.address))
          .mapTo[Seq[TokenMetadata]],
        5.second
      )
      assert(
        query1.length == 1 && query1.head.burnRateForMarket === burnRateRes.forMarket &&
          query1.head.burnRateForP2P == burnRateRes.forP2P && query1.head.usdPrice == 20
      )

      info("send a message to disable lrc")
      val disabled = Await.result(
        (actor ? DisableToken.Req(lrc.address)).mapTo[DisableToken.Res],
        5 second
      )
      assert(disabled.error == ErrorCode.ERR_NONE)
      val query2 = Await.result(
        dbModule.tokenMetadataDal
          .getTokens(Seq(lrc.address))
          .mapTo[Seq[TokenMetadata]],
        5.second
      )
      assert(
        query2.nonEmpty && query2.head.status == TokenMetadata.Status.DISABLED
      )
    }
  }

  "load markets config" must {
    "get all markets config" in {
      info("save some markets config")
      val AAA = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      val BBB = "0x9b9211a2ce4eEE9c5619d54E5CD9f967A68FBE23"
      val CCC = "0x7b22713f2e818fad945af5a3618a2814f102cbe0"
      val DDD = "0x45245bc59219eeaaf6cd3f382e078a461ff9de7b"
      val marketIdLrcWeth = MarketId(primary = DDD, secondary = AAA)
      val marketIdBnbWeth = MarketId(primary = DDD, secondary = BBB)
      val marketIdZrxdWeth = MarketId(primary = DDD, secondary = CCC)
      val marketLrcWeth = MarketMetadata(
        status = MarketMetadata.Status.ENABLED,
        secondaryTokenSymbol = "AAA",
        primaryTokenSymbol = "DDD",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdLrcWeth),
        marketHash = MarketHashProvider.convert2Hex(DDD, AAA)
      )
      val markets = Seq(
        marketLrcWeth,
        MarketMetadata(
          status = MarketMetadata.Status.DISABLED,
          secondaryTokenSymbol = "BBB",
          primaryTokenSymbol = "DDD",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketId = Some(marketIdBnbWeth),
          marketHash = MarketHashProvider.convert2Hex(DDD, BBB)
        ),
        MarketMetadata(
          status = MarketMetadata.Status.READONLY,
          secondaryTokenSymbol = "CCC",
          primaryTokenSymbol = "DDD",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketId = Some(marketIdZrxdWeth),
          marketHash = MarketHashProvider.convert2Hex(DDD, CCC)
        )
      )
      actor ! SaveMarketMetadatas.Req(markets)
      Thread.sleep(3000)

      info("subscriber should received the message")
      probe.expectMsg(MetadataChanged())

      info("query the markets from db")
      val r1 = dbModule.marketMetadataDal.getMarketsByHashes(
        markets.map(_.marketHash)
      )
      val res1 = Await.result(r1.mapTo[Seq[MarketMetadata]], 5.second)
      assert(res1.length == markets.length)

      info("send a message to load markets config")
      val q1 = Await.result(
        (actor ? LoadMarketMetadata.Req()).mapTo[LoadMarketMetadata.Res],
        5.second
      )
      assert(q1.markets.length >= markets.length)

      info("save a new market: ABC-LRC")
      val ABC = "0x244929a8141d2134d9323e65309fb46e4a983840"
      val marketIdAbcLrc = MarketId(primary = ABC, secondary = AAA)
      val abcLrc = MarketMetadata(
        status = MarketMetadata.Status.READONLY,
        secondaryTokenSymbol = "ABC",
        primaryTokenSymbol = "AAA",
        maxNumbersOfOrders = 1000,
        priceDecimals = 3,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 6,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdAbcLrc),
        marketHash = MarketHashProvider.convert2Hex(ABC, AAA)
      )
      val r2 = dbModule.marketMetadataDal.saveMarket(abcLrc)
      val res2 = Await.result(r2.mapTo[ErrorCode], 5.second)
      assert(res2 == ErrorCode.ERR_NONE)

      info(
        s"send a message to update :{priceDecimals = 2, status = MarketMetadata.Status.READONLY}"
      )
      val updated = Await.result(
        (actor ? UpdateMarketMetadata.Req(
          Some(
            marketLrcWeth
              .copy(priceDecimals = 2, status = MarketMetadata.Status.READONLY)
          )
        )).mapTo[UpdateMarketMetadata.Res],
        5.second
      )
      assert(updated.error == ErrorCode.ERR_NONE)
      val q11 = dbModule.marketMetadataDal.getMarketsByHashes(
        Seq(marketLrcWeth.marketHash)
      )
      val res11 = Await.result(q11.mapTo[Seq[MarketMetadata]], 5.second)
      assert(
        res11.length == 1 && res11.head.priceDecimals == 2 && res11.head.status == MarketMetadata.Status.READONLY
      )

      info("send a message to disable lrc-weth")
      val disabled = Await.result(
        (actor ? DisableMarket.Req(marketLrcWeth.marketHash))
          .mapTo[DisableMarket.Res],
        5 second
      )
      assert(disabled.error == ErrorCode.ERR_NONE)
      val query2 = Await.result(
        dbModule.marketMetadataDal
          .getMarketsByHashes(Seq(marketLrcWeth.marketHash))
          .mapTo[Seq[MarketMetadata]],
        5.second
      )
      assert(
        query2.nonEmpty && query2.head.status == MarketMetadata.Status.DISABLED
      )
    }
  }

  "get metadatas" must {
    "call JRPC and get result" in {
      val r = singleRequest(GetMetadatas.Req(), "get_metadatas")
      val res = Await.result(r.mapTo[GetMetadatas.Res], timeout.duration)
      assert(res.tokens.length >= 7 && res.markets.length >= 4)
    }
  }

}
