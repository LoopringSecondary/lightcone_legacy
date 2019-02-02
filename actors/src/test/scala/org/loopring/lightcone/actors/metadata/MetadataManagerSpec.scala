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
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.actors.validator.MetadataManagerValidator
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.pattern._
import org.loopring.lightcone.core.base.MetadataManager

class MetadataManagerSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {

  val probe = TestProbe()
  val mediator = DistributedPubSub(system).mediator
  mediator ! Subscribe(MetadataManagerActor.pubsubTopic, probe.ref)

  def actor = actors.get(MetadataManagerValidator.name)
  def ethereumQueryActor = actors.get(EthereumQueryActor.name)

  "load tokens config" must {
    "initialized metadataManager completely" in {
      info("check tokens: address at lower and upper case")
      assert(metadataManager.getTokens.length >= TOKENS.length) // in case added some tokens after initialized (metadataManager.addToken(token))
      TOKENS.foreach { t =>
        val meta1 = metadataManager.getToken(t.address.toLowerCase())
        val meta2 =
          metadataManager.getToken("0x" + t.address.substring(2).toUpperCase())
        assert(
          meta1.nonEmpty && meta2.nonEmpty && meta1.get.meta.address == meta2.get.meta.address && meta1.get.meta.address == meta1.get.meta.address
            .toLowerCase()
        )
      }
      info("check markets: market addresses at lower and upper case")
      assert(metadataManager.getValidMarketPairs.size >= MARKETS.length)
      MARKETS.foreach { m =>
        val meta1 =
          metadataManager.getMarketMetadata(m.marketHash.toLowerCase())
        val meta2 = metadataManager.getMarketMetadata(
          "0x" + m.marketHash.substring(2).toUpperCase()
        )
        assert(
          meta1.marketHash == meta2.marketHash && meta1.marketHash == meta1.marketHash
            .toLowerCase()
        )

        val meta3 = metadataManager.getMarketMetadata(
          MarketPair(
            baseToken = m.marketPair.get.baseToken.toLowerCase(),
            quoteToken = m.marketPair.get.quoteToken.toLowerCase()
          )
        )
        val meta4 = metadataManager.getMarketMetadata(
          MarketPair(
            baseToken = "0x" + m.marketPair.get.baseToken
              .substring(2)
              .toUpperCase(),
            quoteToken = "0x" + m.marketPair.get.quoteToken
              .substring(2)
              .toUpperCase()
          )
        )
        assert(meta3.marketHash == meta4.marketHash)
      }
    }

    "get all tokens config" in {
      info("save some tokens config")
      val lrc = TokenMetadata(
        `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
        status = TokenMetadata.Status.VALID,
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
          status = TokenMetadata.Status.VALID,
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
          status = TokenMetadata.Status.VALID,
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
          status = TokenMetadata.Status.VALID,
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
          status = TokenMetadata.Status.VALID,
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
          status = TokenMetadata.Status.INVALID,
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
          status = TokenMetadata.Status.INVALID,
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
        (ethereumQueryActor ? GetBurnRate.Req(token = lrc.address))
          .mapTo[GetBurnRate.Res],
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
        (actor ? InvalidateToken.Req(lrc.address)).mapTo[InvalidateToken.Res],
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
        query2.nonEmpty && query2.head.status == TokenMetadata.Status.INVALID
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
      val marketPairLrcWeth = MarketPair(baseToken = DDD, quoteToken = AAA)
      val marketPairBnbWeth = MarketPair(baseToken = DDD, quoteToken = BBB)
      val marketPairZrxdWeth = MarketPair(baseToken = DDD, quoteToken = CCC)
      val marketLrcWeth = MarketMetadata(
        status = MarketMetadata.Status.ACTIVE,
        quoteTokenSymbol = "AAA",
        baseTokenSymbol = "DDD",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPairLrcWeth),
        marketHash = MarketHash(DDD, AAA).toString
      )
      val markets = Seq(
        marketLrcWeth,
        MarketMetadata(
          status = MarketMetadata.Status.TERMINATED,
          quoteTokenSymbol = "BBB",
          baseTokenSymbol = "DDD",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketPair = Some(marketPairBnbWeth),
          marketHash = MarketHash(DDD, BBB).toString
        ),
        MarketMetadata(
          status = MarketMetadata.Status.READONLY,
          quoteTokenSymbol = "CCC",
          baseTokenSymbol = "DDD",
          maxNumbersOfOrders = 1000,
          priceDecimals = 8,
          orderbookAggLevels = 1,
          precisionForAmount = 11,
          precisionForTotal = 12,
          browsableInWallet = true,
          updatedAt = timeProvider.getTimeMillis,
          marketPair = Some(marketPairZrxdWeth),
          marketHash = MarketHash(DDD, CCC).toString
        )
      )
      actor ! SaveMarketMetadatas.Req(markets)
      Thread.sleep(3000)

      info("subscriber should received the message")
      probe.expectMsg(MetadataChanged())

      info("query the markets from db")
      val r1 =
        dbModule.marketMetadataDal.getMarketsByKey(markets.map(_.marketHash))
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
      val marketPairAbcLrc = MarketPair(baseToken = ABC, quoteToken = AAA)
      val abcLrc = MarketMetadata(
        status = MarketMetadata.Status.READONLY,
        quoteTokenSymbol = "ABC",
        baseTokenSymbol = "AAA",
        maxNumbersOfOrders = 1000,
        priceDecimals = 3,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 6,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPairAbcLrc),
        marketHash = MarketHash(ABC, AAA).toString
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
      val q11 =
        dbModule.marketMetadataDal.getMarketsByKey(
          Seq(marketLrcWeth.marketHash)
        )
      val res11 = Await.result(q11.mapTo[Seq[MarketMetadata]], 5.second)
      assert(
        res11.length == 1 && res11.head.priceDecimals == 2 && res11.head.status == MarketMetadata.Status.READONLY
      )

      info("send a message to disable lrc-weth")
      val disabled = Await.result(
        (actor ? TerminateMarket.Req(marketLrcWeth.marketHash))
          .mapTo[TerminateMarket.Res],
        5 second
      )
      assert(disabled.error == ErrorCode.ERR_NONE)
      val query2 = Await.result(
        dbModule.marketMetadataDal
          .getMarketsByKey(Seq(marketLrcWeth.marketHash))
          .mapTo[Seq[MarketMetadata]],
        5.second
      )
      assert(
        query2.nonEmpty && query2.head.status == MarketMetadata.Status.TERMINATED
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

  "token and market format" must {
    "format token address and symbol" in {
      val a = TokenMetadata(
        `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
        status = TokenMetadata.Status.VALID,
        symbol = "aaa",
        name = "aaa Token",
        address = "0x1c1b9d3819ab7a3da0353fe0f9e41d3f89192cf8",
        unit = "aaa",
        decimals = 18,
        precision = 6,
        burnRateForMarket = 0.1,
        burnRateForP2P = 0.2,
        usdPrice = 10
      )
      val b = TokenMetadata(
        `type` = TokenMetadata.Type.TOKEN_TYPE_ERC20,
        status = TokenMetadata.Status.VALID,
        symbol = "abc",
        name = "ABC Token",
        address = "0x255Aa6DF07540Cb5d3d297f0D0D4D84cb52bc8e6",
        unit = "ABC",
        decimals = 18,
        precision = 6,
        burnRateForMarket = 0.3,
        burnRateForP2P = 0.4,
        usdPrice = 8
      )
      info("token A and formatedA should same")
      val formatedA = MetadataManager.normalizeToken(a)
      assert(
        formatedA.address == a.address && formatedA.symbol == a.symbol
          .toUpperCase()
      )

      info("token B formated address and symbol should same with formatedB")
      val formatedB = MetadataManager.normalizeToken(b)
      assert(
        b.address.toLowerCase() == formatedB.address && b.symbol
          .toUpperCase() == formatedB.symbol
      )
    }

    "format market" in {
      val AAA = "0xF51DF14E49DA86ABC6F1D8CCC0B3A6B7B7C90CA6"
      val BBB = "0x9B9211A2CE4EEE9C5619D54E5CD9F967A68FBE23"
      val marketPair = MarketPair(baseToken = BBB, quoteToken = AAA)
      val market = MarketMetadata(
        status = MarketMetadata.Status.ACTIVE,
        quoteTokenSymbol = "aaa",
        baseTokenSymbol = "bbb",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPair),
        marketHash = MarketHash(BBB, AAA).toString
      )
      val formatedMarket = MetadataManager.normalizeMarket(market)
      assert(
        market.baseTokenSymbol == "bbb" &&
          formatedMarket.baseTokenSymbol == "BBB"
      )
      assert(
        market.quoteTokenSymbol == "aaa" &&
          formatedMarket.quoteTokenSymbol == "AAA"
      )
      val formatedMarketPair = formatedMarket.marketPair.get
      assert(
        marketPair.baseToken == "0x9B9211A2CE4EEE9C5619D54E5CD9F967A68FBE23" &&
          formatedMarketPair.baseToken == "0x9b9211a2ce4eee9c5619d54e5cd9f967a68fbe23"
      )
      assert(
        marketPair.quoteToken == "0xF51DF14E49DA86ABC6F1D8CCC0B3A6B7B7C90CA6" &&
          formatedMarketPair.quoteToken == "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
      )
      assert(
        market.marketHash == "0x6e8fe0ec8794683790e80d829c6a5fd01146b285" && formatedMarket.marketHash == "0x6e8fe0ec8794683790e80d829c6a5fd01146b285"
      )
    }
  }

}
