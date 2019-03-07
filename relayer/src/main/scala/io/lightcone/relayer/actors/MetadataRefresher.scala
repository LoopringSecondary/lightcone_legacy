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
import io.lightcone.relayer.implicits._
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
        _ <- refreshMetadata()
        _ = getLocalActors().foreach(_ ! req)
      } yield Unit

    case req: GetTokens.Req => {
      val tokens_ = if (tokens.nonEmpty) {
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
          val currencySymbols = Currency.values.map(_.name)
          tokens
            .filter(t => !currencySymbols.contains(t.getSymbol()))
            .:+(eth)
        }
      } else {
        Seq.empty
      }
      val res = tokens_.map { t =>
        val metadata = if (req.requireMetadata) t.metadata else None
        val info = if (req.requireInfo) t.info else None
        val price = if (req.requirePrice) {
          changeUsdPriceWithQuoteCurrency(t.price, req.quoteCurrencyForPrice)
        } else 0.0
        Token(metadata, info, price)
      }
      sender ! GetTokens.Res(res)
    }

    case req: GetMarkets.Req =>
      // TODO(yongfeng): req.queryLoopringTicker
      val markets_ = if (req.marketPairs.nonEmpty) {
        markets.filter(
          m => req.marketPairs.contains(m.getMetadata.marketPair.get)
        )
      } else {
        markets
      }
      val res = markets_.map { m =>
        val metadata = if (req.requireMetadata) m.metadata else None
        val ticker = if (req.requireTicker) {
          val t = m.ticker.get
          val price =
            changeUsdPriceWithQuoteCurrency(t.price, req.quoteCurrencyForTicker)
          Some(t.copy(price = price))
        } else None
        Market(metadata, ticker)
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
      tokens = tokens_
      markets = markets_
      metadataManager.reset(tokens, markets)
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

  private def changeUsdPriceWithQuoteCurrency(
      priceInUsd: Double,
      toCurrency: Currency
    ) = {
    toCurrency match {
      case Currency.NOT_USE | Currency.USD =>
        calculate(priceInUsd, 1, 2)
      case m =>
        val precision =
          if (QUOTE_TOKEN.contains(m.name)) { // ETH, BTC will set to 8 or token's precision
            val quoteTokenOpt = tokens.find(_.getSymbol() == m.name)
            if (quoteTokenOpt.nonEmpty)
              quoteTokenOpt.get.getMetadata.precision | 8
            else 8
          } else 2 // RMB, JPY
        val currencyToken = tokens
          .find(_.getSymbol() == m.name)
          .getOrElse(Token()) // price = 0 if not found currency token
        calculate(priceInUsd, currencyToken.price, precision)
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
}
