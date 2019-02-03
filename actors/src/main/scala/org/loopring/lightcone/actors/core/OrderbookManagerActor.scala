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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.core.OrderbookManagerActor.getEntityId
import org.loopring.lightcone.actors.utils.MetadataRefresher
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto._
import org.slf4s.Logging

import scala.concurrent._
import scala.util.{Failure, Success}

// Owner: Hongyu
object OrderbookManagerActor extends ShardedByMarket with Logging {
  val name = "orderbook_manager"

  def getTopicId(marketPair: MarketPair) =
    OrderbookManagerActor.name + "-" + getEntityId(marketPair)

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSharding(Props(new OrderbookManagerActor()))
  }

  // 如果message不包含一个有效的marketPair，就不做处理，不要返回“默认值”
  val extractMarketPair: PartialFunction[Any, MarketPair] = {
    case GetOrderbook.Req(_, _, Some(marketPair)) => marketPair

    case Orderbook.Update(_, _, _, Some(marketPair)) => marketPair

    case Notify(KeepAliveActor.NOTIFY_MSG, marketPairStr) =>
      val tokens = marketPairStr.split("-")
      MarketPair(tokens(0), tokens(1))
  }
}

class OrderbookManagerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val metadataManager: MetadataManager)
    extends InitializationRetryActor
    with ShardingEntityAware
    with RepeatedJobActor {

  val selfConfig = config.getConfig(OrderbookManagerActor.name)
  val orderbookRecoverSize = selfConfig.getInt("orderbook-recover-size")
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  val marketPair = metadataManager.getValidMarketPairs.values
    .find(m => getEntityId(m) == entityId.toString)
    .get

  def marketMetadata = metadataManager.getMarketMetadata(marketPair)
  def marketManagerActor = actors.get(MarketManagerActor.name)
  val marketPairHashedValue = OrderbookManagerActor.getEntityId(marketPair)
  val manager: OrderbookManager = new OrderbookManagerImpl(marketMetadata)

  val repeatedJobs = Seq(
    Job(
      name = "load_orderbook_from_market",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncOrderbookFromMarket()
    )
  )

  actors.get(MetadataRefresher.name) ! SubscribeMetadataChanged()

  def ready: Receive = super.receiveRepeatdJobs orElse {
    case req @ Notify(KeepAliveActor.NOTIFY_MSG, _) =>
      sender ! req

    case req: Orderbook.Update =>
      log.info(s"receive Orderbook.Update ${req}")
      manager.processUpdate(req)

    case GetOrderbook.Req(level, size, Some(marketPair)) =>
      Future {
        if (OrderbookManagerActor
              .getEntityId(marketPair) == marketPairHashedValue)
          GetOrderbook.Res(Option(manager.getOrderbook(level, size)))
        else
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"marketPair doesn't match, expect: ${marketPair} ,receive: ${marketPair}"
          )
      } sendTo sender

    case req: MetadataChanged =>
      val metadataOpt = try {
        Option(metadataManager.getMarketMetadata(marketPair))
      } catch {
        case _: Throwable => None
      }
      metadataOpt match {
        case None => context.system.stop(self)
        case Some(metadata) if metadata.status.isTerminated =>
          context.system.stop(self)
        case Some(metadata) =>
          log.debug(
            s"metadata.status is ${metadata.status},so needn't to stop ${self.path.address}"
          )
      }
  }

  private def syncOrderbookFromMarket() =
    for {
      res <- (marketManagerActor ? GetOrderbookSlots.Req(
        Some(marketPair),
        orderbookRecoverSize
      )).mapTo[GetOrderbookSlots.Res]
      _ = log.debug(s"orderbook synced: ${res}")
    } yield {
      if (res.update.nonEmpty) {
        manager.processUpdate(res.update.get)
      }
    }

}
