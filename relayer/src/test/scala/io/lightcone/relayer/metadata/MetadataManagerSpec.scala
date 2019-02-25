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

package io.lightcone.relayer.metadata

import java.util.Calendar
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.testkit.TestProbe
import io.lightcone.core._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.support._
import io.lightcone.relayer.validator._
import io.lightcone.relayer.data._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import akka.pattern._
import io.lightcone.persistence.{CMCCrawlerConfigForToken, ExternalTicker}
import io.lightcone.relayer.data.cmc._
import io.lightcone.relayer.external.{CMCExternalTickerFetcher, USD_RMB}
import scalapb.json4s.Parser

class MetadataManagerSpec
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with DatabaseModuleSupport
    with MetadataManagerSupport {
  import MarketMetadata.Status._

  val probe = TestProbe()
  val mediator = DistributedPubSub(system).mediator
  mediator ! Subscribe(MetadataManagerActor.pubsubTopic, probe.ref)

  def actor = actors.get(MetadataManagerValidator.name)
  def ethereumQueryActor = actors.get(EthereumQueryActor.name)

  "load tokens config" must {
    "" in {
      val now = Calendar.getInstance()
      for (i <- 0 to 59) {
        now.set(Calendar.MINUTE, i)
        val delayInSeconds =
          MetadataManagerActor.calculateInitialDelayInSecondWithFixInterval(now)
      }
    }

    "initialize tickers" in {
      val f = for {
        cmcResponse <- getMockedCMCTickers()
        rateResponse <- fiatExchangeRateFetcher.fetchExchangeRates(Seq(USD_RMB))
        slugSymbols_ <- dbModule.cmcTickerConfigDal.getConfigs()
        tickersToPersist <- if (cmcResponse.data.nonEmpty && rateResponse.nonEmpty && rateResponse
                                  .contains(USD_RMB)) {
          persistTickers(rateResponse(USD_RMB), cmcResponse.data, slugSymbols_)
        } else {
          Future.successful(Seq.empty)
        }
      } yield {}
      Await.result(f.mapTo[Unit], 30.second)
    }

    "initialized metadataManager completely" in {
      info("check tokens: address at lower and upper case")
      assert(metadataManager.getTokens.length >= TOKENS.length) // in case added some tokens after initialized (metadataManager.addToken(token))
      TOKENS.foreach { t =>
        val meta1 = metadataManager.getTokenWithAddress(t.address.toLowerCase())
        val meta2 =
          metadataManager.getTokenWithAddress(
            "0x" + t.address.substring(2).toUpperCase()
          )
        assert(
          meta1.nonEmpty && meta2.nonEmpty && meta1.get.meta.address == meta2.get.meta.address && meta1.get.meta.address == meta1.get.meta.address
            .toLowerCase()
        )
      }
      info("check markets: market addresses at lower and upper case")
      assert(
        metadataManager.getMarkets(ACTIVE, READONLY).size >= MARKETS.length
      )
      MARKETS.foreach { m =>
        val meta1 =
          metadataManager.getMarket(m.marketHash.toLowerCase())
        val meta2 = metadataManager.getMarket(
          "0x" + m.marketHash.substring(2).toUpperCase()
        )
        assert(
          meta1.marketHash == meta2.marketHash && meta1.marketHash == meta1.marketHash
            .toLowerCase()
        )

        val meta3 = metadataManager.getMarket(
          MarketPair(
            baseToken = m.marketPair.get.baseToken.toLowerCase(),
            quoteToken = m.marketPair.get.quoteToken.toLowerCase()
          )
        )
        val meta4 = metadataManager.getMarket(
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
        marketHash = MarketHash(MarketPair(DDD, AAA)).toString
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
          marketHash = MarketHash(MarketPair(DDD, BBB)).toString
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
          marketHash = MarketHash(MarketPair(DDD, CCC)).toString
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
        marketHash = MarketHash(MarketPair(ABC, AAA)).toString
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
      assert(res.tokens.length >= 4 && res.markets.length >= 4)
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
        externalData = Some(TokenMetadata.ExternalData(10))
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
        externalData = Some(TokenMetadata.ExternalData(8))
      )
      info("token A and formatedA should same")
      val formatedA = MetadataManager.normalize(a)
      assert(
        formatedA.address == a.address && formatedA.symbol == a.symbol
          .toUpperCase()
      )

      info("token B formated address and symbol should same with formatedB")
      val formatedB = MetadataManager.normalize(b)
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
        marketHash = MarketHash(MarketPair(BBB, AAA)).toString
      )
      val formatedMarket = MetadataManager.normalize(market)
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

  val parser = new Parser(preservingProtoFieldNames = true) //protobuf 序列化为json不使用驼峰命名

  private def getMockedCMCTickers() = {
    import scala.io.Source
    val fileContents = Source.fromResource("cmc.data").getLines.mkString

    val res = parser.fromJsonString[TickerDataInfo](fileContents)
    res.status match {
      case Some(r) if r.errorCode == 0 =>
        Future.successful(res.copy(data = res.data))
      case Some(r) if r.errorCode != 0 =>
        log.error(
          s"Failed request CMC, code:[${r.errorCode}] msg:[${r.errorMessage}]"
        )
        Future.successful(res)
      case m =>
        log.error(s"Failed request CMC, return:[$m]")
        Future.successful(TickerDataInfo(Some(TickerStatus(errorCode = 404))))
    }
  }

  private def persistTickers(
      usdToCnyRate: Double,
      tickers_ : Seq[CMCTickerData],
      slugSymbols: Seq[CMCCrawlerConfigForToken]
    ) =
    for {
      _ <- Future.unit
      tickersToPersist = CMCExternalTickerFetcher
        .convertCMCResponseToPersistence(
          tickers_,
          slugSymbols
        )
      cnyTicker = ExternalTicker(
        "RMB",
        CMCExternalTickerFetcher
          .toDouble(BigDecimal(1) / BigDecimal(usdToCnyRate))
      )
      now = timeProvider.getTimeSeconds()
      tickers = tickersToPersist.+:(cnyTicker).map(t => t.copy(timestamp = now))
      fixGroup = tickers.grouped(20).toList
      _ <- Future.sequence(
        fixGroup.map(dbModule.externalTickerDal.saveTickers)
      )
      updateSucc <- dbModule.externalTickerDal.setValid(now)
    } yield {
      if (updateSucc != ErrorCode.ERR_NONE) {
        log.error(s"CMC persist failed, code:$updateSucc")
        Seq.empty
      } else {
        tickers
      }
    }
}
