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

  def getTopicId(marketId: MarketId) =
    OrderbookManagerActor.name + "-" + getEntityId(marketId)

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

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("instances-per-market")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderbookManagerActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的marketId，就不做处理，不要返回“默认值”
  val extractMarketId: PartialFunction[Any, MarketId] = {
    case GetOrderbook.Req(_, _, Some(marketId)) => marketId

    case Orderbook.Update(_, _, _, Some(marketId)) => marketId

    case Notify(KeepAliveActor.NOTIFY_MSG, marketIdStr) =>
      val tokens = marketIdStr.split("-")
      MarketId(tokens(0), tokens(1))
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
    extends ActorWithPathBasedConfig(
      OrderbookManagerActor.name,
      OrderbookManagerActor.extractEntityId
    )
    with RepeatedJobActor {

  val orderbookRecoverSize = selfConfig.getInt("orderbook-recover-size")
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  val marketId = metadataManager.getValidMarketIds.values
    .find(m => getEntityId(m) == entityId)
    .get

  def marketMetadata = metadataManager.getMarketMetadata(marketId)
  def marketManagerActor = actors.get(MarketManagerActor.name)
  val marketIdHashedValue = OrderbookManagerActor.getEntityId(marketId)
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

    case GetOrderbook.Req(level, size, Some(marketId)) =>
      Future {
        if (OrderbookManagerActor.getEntityId(marketId) == marketIdHashedValue)
          GetOrderbook.Res(Option(manager.getOrderbook(level, size)))
        else
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            s"marketId doesn't match, expect: ${marketId} ,receive: ${marketId}"
          )
      } sendTo sender

    case req: MetadataChanged =>
      val metadataOpt = try {
        Option(metadataManager.getMarketMetadata(marketId))
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
        Some(marketId),
        orderbookRecoverSize
      )).mapTo[GetOrderbookSlots.Res]
      _ = log.debug(s"orderbook synced: ${res}")
    } yield {
      if (res.update.nonEmpty) {
        manager.processUpdate(res.update.get)
      }
    }

}
