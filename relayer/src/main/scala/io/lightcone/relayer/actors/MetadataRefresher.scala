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
import io.lightcone.relayer.implicits._
import io.lightcone.persistence._
import io.lightcone.core._
import io.lightcone.relayer.data._
import scala.concurrent._
import scala.util._
import scala.concurrent.duration._

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

  private var currencies = Map.empty[String, Token]

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
    case req: MetadataChanged => {
      for {
        _ <- refreshMetadata()
        _ = getLocalActors(
          MarketManagerActor.name,
          OrderbookManagerActor.name,
          MultiAccountManagerActor.name
        ).foreach(_ ! req)
        _ = delayNotify(req)
      } yield Unit
    }

    case req: NotifyChanged =>
      getLocalActors(SocketIONotificationActor.name)
        .foreach(_ ! req.metadataChanged)

    case req: GetTokens.Req => {
      val requestTokens =
        if (req.tokens.nonEmpty)
          req.tokens
            .map(metadataManager.getTokenWithAddress)
            .filter(_.nonEmpty)
            .map(_.get)
        else
          metadataManager.getTokens()

      val res = requestTokens.map { t =>
        val metadataOpt = if (req.requireMetadata) t.metadata else None
        val infoOpt = if (req.requireInfo) t.info else None
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
      if (!currencies.contains(req.quoteCurrencyForTicker.name)) {
        throw ErrorException(
          ErrorCode.ERR_INTERNAL_UNKNOWN,
          s"not found exchange rate of currency:${req.quoteCurrencyForTicker.name}"
        )
      }
      val markets_ = if (req.marketPairs.isEmpty) {
        metadataManager.getMarkets()
      } else {
        req.marketPairs.map(metadataManager.getMarket)
      }
      val res = markets_.map { m =>
        val metadataOpt = if (req.requireMetadata) m.metadata else None
        val tickerOpt = if (!req.requireTicker) {
          None
        } else {
          val baseTokenTicker = changeTokenTickerWithQuoteCurrency(
            metadataManager
              .getTokenWithSymbol(m.getMetadata.baseTokenSymbol)
              .get
              .getTicker,
            req.quoteCurrencyForTicker
          )
          Some(
            m.getTicker.copy(
              price = baseTokenTicker.price,
              volume24H = baseTokenTicker.volume24H
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
      currencies = tokens_.map { t =>
        (Currency.fromName(t.getMetadata.symbol), t)
      }.filter(_._1.nonEmpty)
        .map {
          case (Some(c), t) => c.name -> t
        }
        .toMap
      currencies = currencies + (Currency.USD.name -> Token(
        ticker = Some(
          TokenTicker(
            price = 1
          )
        )
      ))
      metadataManager.reset(tokens_.filterNot { t =>
        currencies.contains(t.getMetadata.symbol)
      }, markets_)
    }

  //文档：https://doc.akka.io/docs/akka/2.5/general/addressing.html#actor-path-anchors
  private def getLocalActors(actorNames: String*) = {
    val str = s"akka://${context.system.name}/system/sharding/%s/*/*"

    actorNames map { n =>
      context.system.actorSelection(str.format(n))
    }
  }

  private def delayNotify(changed: MetadataChanged) = {
    if (changed.marketMetadataChanged || changed.tokenMetadataChanged || changed.tickerChanged) {
      context.system.scheduler
        .scheduleOnce(
          30 second,
          self,
          NotifyChanged(changed.copy(tokenInfoChanged = false))
        )
    }
  }

  private def changeTokenTickerWithQuoteCurrency(
      baseTokenTicker: TokenTicker,
      toCurrency: Currency
    ) = {
    val precision = toCurrency.pricePrecision()
    val currencyTicker = currencies(toCurrency.name).getTicker
    baseTokenTicker.copy(
      price = calculate(baseTokenTicker.price, currencyTicker.price, precision),
      volume24H =
        calculate(baseTokenTicker.volume24H, currencyTicker.price, precision)
    )
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

case class NotifyChanged(metadataChanged: MetadataChanged)
