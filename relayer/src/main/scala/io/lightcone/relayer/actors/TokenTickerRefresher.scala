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
import io.lightcone.relayer.data._
import scala.concurrent._
import scala.util._

// Owner: YongFeng
object TokenTickerRefresher {
  val name = "token_ticker_refresher"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ) = {
    system.actorOf(Props(new TokenTickerRefresher()), TokenTickerRefresher.name)
  }
}

// main owner: YongFeng
class TokenTickerRefresher(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with Stash
    with ActorLogging {
  def tokenTickerCrawlerActor = actors.get(CMCCrawlerActor.name)

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(CMCCrawlerActor.pubsubTopic, self)

  private var tickers: Seq[TokenTickerInfo] = Seq.empty[TokenTickerInfo]

  override def initialize() = {
    val f = refreshTickers()
    f onComplete {
      case Success(_) => becomeReady()
      case Failure(e) => throw e
    }
    f
  }

  def ready: Receive = {
    case _: TokenTickerChanged =>
      refreshTickers()

    case req: GetTokenTickers.Req =>
      val tickers_ = if (req.market.isEmpty) {
        tickers
      } else {
        tickers.filter(_.market == req.market)
      }
      sender ! GetTokenTickers.Res(tickers_)
  }

  private def refreshTickers() =
    for {
      tickers_ <- (tokenTickerCrawlerActor ? GetTokenTickers.Req())
        .mapTo[GetTokenTickers.Res]
        .map(_.tickers)
    } yield {
      assert(tickers_.nonEmpty)
      tickers = tickers_
    }
}
