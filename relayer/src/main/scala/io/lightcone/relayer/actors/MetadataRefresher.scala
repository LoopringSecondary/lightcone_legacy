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
  def metadataManagerActor = actors.get(MetadataManagerActor.name)
  def cmcCrawlerActor = actors.get(CMCCrawlerActor.name)

  val mediator = DistributedPubSub(context.system).mediator

  private var tokenMetadatas = Seq.empty[TokenMetadata]
  private var tokenInfos = Seq.empty[TokenInfo]
  private var tokenTickers = Seq.empty[TokenTicker]
  private var markets = Seq.empty[MarketMetadata]

  override def initialize() = {
    val f = for {
      _ <- mediator ? Subscribe(MetadataManagerActor.pubsubTopic, self)
      _ <- refreshMetadata()
    } yield {}

    f onComplete {
      case Success(_) => becomeReady()
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

    case _: GetMetadatas.Req => {
      val details = fillTokenDetail(tokenMetadatas, tokenInfos, tokenTickers)
      sender ! GetMetadatas.Res(tokens = details, markets = markets)
    }
  }

  private def refreshMetadata() =
    for {
      tokenMetadatas_ <- (metadataManagerActor ? LoadTokenMetadata.Req())
        .mapTo[LoadTokenMetadata.Res]
        .map(_.tokens)
      markets_ <- (metadataManagerActor ? LoadMarketMetadata.Req())
        .mapTo[LoadMarketMetadata.Res]
        .map(_.markets)
      tokenTickers_ <- (cmcCrawlerActor ? GetTokenTickers.Req())
        .mapTo[GetTokenTickers.Res]
        .map(_.tickers)
      tokenInfos_ <- dbModule.tokenInfoDal.getTokens()
    } yield {
      assert(tokenMetadatas_.nonEmpty)
      assert(markets_.nonEmpty)
      assert(tokenTickers_.nonEmpty)
      tokenMetadatas = tokenMetadatas_.map(MetadataManager.normalize)
      markets = markets_.map(MetadataManager.normalize)
      tokenTickers = tokenTickers_
      tokenInfos = tokenInfos_
      metadataManager.reset(tokenMetadatas_, tokenTickers_, markets_)
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

  private def fillTokenDetail(
      tokenMetadatas: Seq[TokenMetadata],
      tokenInfos: Seq[TokenInfo],
      tokenTickers: Seq[TokenTicker]
    ): Seq[TokenDetail] = {
    val infoMap = tokenInfos.map(i => i.symbol -> i).toMap
    val tickerMap = tokenTickers.map(t => t.symbol -> t.usdPrice).toMap

    tokenMetadatas.map { m =>
      val i = infoMap.getOrElse(m.symbol, TokenInfo())
      TokenDetail(
        m.`type`,
        m.status,
        m.symbol,
        m.name,
        m.address,
        m.unit,
        m.decimals,
        m.burnRateForMarket,
        m.burnRateForP2P,
        tickerMap.getOrElse(m.symbol, 0),
        i.circulatingSupply,
        i.totalSupply,
        i.maxSupply,
        i.cmcRank,
        i.websiteUrl,
        i.precision
      )
    }
  }

}
