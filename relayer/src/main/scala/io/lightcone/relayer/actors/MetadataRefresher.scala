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

package io.lightcone.relayer.actors

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.lib._
import io.lightcone.relayer.base._
import io.lightcone.persistence._
import io.lightcone.core._
import io.lightcone.relayer.data._
import scala.concurrent._
import scala.util._
import io.lightcone.relayer.external._

// Owner: Hongyu
object MetadataRefresher {
  val name = "metadata_refresher"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      metadataManager: MetadataManager
    ) = {
    system.actorOf(Props(new MetadataRefresher()), MetadataRefresher.name)
  }
}

// main owner: 杜永丰
class MetadataRefresher(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val metadataManager: MetadataManager)
    extends InitializationRetryActor
    with Stash
    with ActorLogging {

  @inline def metadataManagerActor = actors.get(MetadataManagerActor.name)

  val mediator = DistributedPubSub(context.system).mediator

  private var tokens = Seq.empty[Token]
  private var markets = Seq.empty[Market]

  override def initialize() = {
    val f = for {
      _ <- mediator ? Subscribe(MetadataManagerActor.pubsubTopic, self)
      _ <- refreshMetadata()
    } yield {}

    f onComplete {
      case Success(_) =>
        becomeReady()
      case Failure(e) => throw e
    }
    f
  }

  def ready: Receive = {
    case req: MetadataChanged =>
      for {
        (tokenMetadatas, marketMetadatas, tickerChanged) <- refreshMetadata()
        _ = getLocalActors().foreach(_ ! req)
        _ = notifyLocalSocketActor(
          tokenMetadatas,
          marketMetadatas,
          tickerChanged
        )
      } yield Unit

    case req: GetTokens.Req => {
      val tokens_ = if (tokens.isEmpty) {
        Seq.empty
      } else {
        if (req.tokens.nonEmpty) {
          tokens
            .filter(
              t =>
                t.metadata.nonEmpty && t.getMetadata.address.nonEmpty && req.tokens
                  .contains(t.getMetadata.address)
            )
        } else {
          val eth = tokens
            .find(t => t.getSymbol() == Currency.ETH.name)
            .getOrElse(
              throw ErrorException(
                ErrorCode.ERR_INTERNAL_UNKNOWN,
                "not found ticker ETH"
              )
            )
          tokens
            .filter(_.getMetadata.`type` != TokenMetadata.Type.TOKEN_TYPE_ETH)
            .:+(eth)
        }
      }
      val res = tokens_.map { t =>
        val metadataOpt = if (!req.requireMetadata) None else t.metadata
        val infoOpt = if (!req.requireInfo) None else t.info
        val tickerOpt = if (!req.requirePrice) {
          None
        } else {
          Some(
            changeTokenTickerWithQuoteCurrency(
              t.getTicker,
              req.quoteCurrencyForPrice
            )
          )
        }
        Token(metadataOpt, infoOpt, tickerOpt)
      }
      sender ! GetTokens.Res(res)
    }

    case req: GetMarkets.Req =>
      // TODO(yongfeng): req.queryLoopringTicker
      val markets_ = if (req.marketPairs.isEmpty) {
        markets
      } else {
        markets.filter(
          m => req.marketPairs.contains(m.getMetadata.marketPair.get)
        )
      }
      val res = markets_.map { m =>
        val metadataOpt = if (!req.requireMetadata) None else m.metadata
        val tickerOpt = if (!req.requireTicker) {
          None
        } else {
          Some(
            changeMarketTickerWithQuoteCurrency(
              m.ticker.get,
              req.quoteCurrencyForTicker
            )
          )
        }
        Market(metadataOpt, tickerOpt)
      }
      sender ! GetMarkets.Res(res)
  }

  private def refreshMetadata() =
    for {
      tokens_ <- (metadataManagerActor ? GetTokens.Req())
        .mapTo[GetTokens.Res]
        .map(_.tokens)
      markets_ <- (metadataManagerActor ? GetMarkets.Req())
        .mapTo[GetMarkets.Res]
        .map(_.markets)
    } yield {
      assert(tokens_.nonEmpty)
      assert(markets_.nonEmpty)
      val diff = calculateDiff(tokens_, markets_)
      tokens = tokens_
      markets = markets_
      metadataManager.reset(tokens: Seq[Token], markets: Seq[Market])
      diff
    }

  private def calculateDiff(
      tokens_ : Seq[Token],
      markets_ : Seq[Market]
    ) = {
    val tokenMetadata_ = tokens_.map(_.getMetadata)
    val tokenMetadata = tokens.map(_.getMetadata)
    val tokenTicker_ = tokens_.map(_.getTicker)
    val tokenTicker = tokens.map(_.getTicker)
    val marketMetadata_ = markets_.map(_.getMetadata)
    val marketMetadata = markets.map(_.getMetadata)
    val tokenMetadataChanged = tokenMetadata_.diff(tokenMetadata)
    val marketMetadataChanged = marketMetadata_.diff(marketMetadata)
    val tickerChanged = tokenTicker_.diff(tokenTicker)
    (tokenMetadataChanged, marketMetadataChanged, tickerChanged.nonEmpty)
  }

  //文档：https://doc.akka.io/docs/akka/2.5/general/addressing.html#actor-path-anchors
  private def getLocalActors() = {
    val str = s"akka://${context.system.name}/system/sharding/%s/*/*"

    Seq(
      context.system.actorSelection(str.format(OrderbookManagerActor.name)),
      context.system.actorSelection(str.format(OrderbookManagerActor.name)),
      context.system.actorSelection(str.format(MultiAccountManagerActor.name))
    )
  }

  private def notifyLocalSocketActor(
      tokenMetadatas: Seq[TokenMetadata],
      marketMetadatas: Seq[MarketMetadata],
      tickerChanged: Boolean
    ) = {
    val str = s"akka://${context.system.name}/system/sharding/%s/*/*"
    val targetActor =
      context.system.actorSelection(str.format(SocketIONotificationActor.name))
    tokenMetadatas.foreach(t => targetActor ! t)
    marketMetadatas.foreach(t => targetActor ! t)
    if (tickerChanged) targetActor ! TokenTickerChanged
  }

  private def changeTokenTickerWithQuoteCurrency(
      baseTokenTicker: TokenTicker,
      toCurrency: Currency
    ) = {
    val precision = calcuatePricePrecision(toCurrency)
    val price = calculatePrice(baseTokenTicker.price, toCurrency, precision)
    if (toCurrency == Currency.USD) {
      baseTokenTicker.copy(price = price)
    } else {
      val volume24H =
        BigDecimal(baseTokenTicker.volume24H / baseTokenTicker.price * price)
          .setScale(precision, BigDecimal.RoundingMode.HALF_UP)
          .toDouble
      baseTokenTicker.copy(price = price, volume24H = volume24H)
    }
  }

  private def calcuatePricePrecision(quoteCurrency: Currency) = {
    if (QUOTE_TOKEN.contains(quoteCurrency.name)) { // ETH, BTC will set to 8 or token's precision
      val quoteTokenOpt = tokens.find(_.getSymbol() == quoteCurrency.name)
      if (quoteTokenOpt.nonEmpty)
        quoteTokenOpt.get.getMetadata.precision | 8
      else 8
    } else 2 // RMB, JPY
  }

  private def calculatePrice(
      basePrice: Double,
      toCurrency: Currency,
      precision: Int
    ) = {
    toCurrency match {
      case Currency.USD =>
        calculate(basePrice, 1, 2)
      case _ =>
        val currencyToken = tokens
          .find(_.getSymbol() == toCurrency.name)
          .getOrElse(
            throw ErrorException(
              ErrorCode.ERR_INTERNAL_UNKNOWN,
              s"not found ticker of token symbol:${toCurrency.name}"
            )
          )
        calculate(basePrice, currencyToken.getTicker.price, precision)
    }
  }

  private def calculate(
      p1: Double,
      p2: Double,
      scale: Int
    ) =
    BigDecimal(p1 / p2)
      .setScale(scale, BigDecimal.RoundingMode.HALF_UP)
      .toDouble

  private def changeMarketTickerWithQuoteCurrency(
      marketTicker: MarketTicker,
      toCurrency: Currency
    ) = {
    val precision = calcuatePricePrecision(toCurrency)
    if (toCurrency == Currency.USD) {
      marketTicker.copy(price = calculate(marketTicker.price, 1, precision))
    } else {
      val baseTicker = getTickerByAddress(marketTicker.baseToken)
      val quoteTicker = tokens
        .find(_.getSymbol() == toCurrency.name)
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found token of symbol:${toCurrency.name}"
          )
        )
        .ticker
        .getOrElse(
          throw ErrorException(
            ErrorCode.ERR_INTERNAL_UNKNOWN,
            s"not found ticker of symbol:${toCurrency.name}"
          )
        )
      val rateToQuoteToken =
        calculate(baseTicker.price, quoteTicker.price, precision)
      val volume24H =
        calculate(baseTicker.volume24H, quoteTicker.price, precision)
      marketTicker.copy(price = rateToQuoteToken, volume24H = volume24H)
    }
  }

  private def getTickerByAddress(address: String) = {
    tokens
      .find(t => t.getAddress() == address)
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found token of address: $address"
        )
      )
      .ticker
      .getOrElse(
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found ticker of address: $address"
        )
      )
  }
}
