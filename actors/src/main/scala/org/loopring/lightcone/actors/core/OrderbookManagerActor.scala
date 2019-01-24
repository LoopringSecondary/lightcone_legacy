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
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.core.OrderbookManagerActor.getEntityId
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.lib.MarketHashProvider._
import org.loopring.lightcone.proto._
import org.slf4s.Logging
import scala.concurrent._
import org.loopring.lightcone.actors.data._

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
    case GetOrderbook.Req(_, _, Some(marketId))    => marketId
    case Orderbook.Update(_, _, _, Some(marketId)) => marketId
    case Notify(KeepAliveActor.NOTIFY_MSG, marketIdStr) =>
      val tokens = marketIdStr.split("-")
      val (primary, secondary) = (tokens(0), tokens(1))
      MarketId(primary, secondary)
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
    with MarketStatusSupport {

  val marketId = metadataManager.getValidMarketIds.values
    .find(m => getEntityId(m) == entityId)
    .get

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe(OrderbookManagerActor.getTopicId(marketId), self)

  val marketMetadata = metadataManager
    .getMarketMetadata(marketId.keyHex())
    .getOrElse(
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"not found market:$marketId config"
      )
    )

  val marketIdHashedValue = OrderbookManagerActor.getEntityId(marketId)

  val manager: OrderbookManager = new OrderbookManagerImpl(marketMetadata)

  def ready: Receive = LoggingReceive {
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
    case msg => log.info(s"not supported msg:${msg}, ${marketId}")

  }

  def processMarketmetaChange(marketMetadata: MarketMetadata): Unit = {
    marketMetadata.status match {
      case MarketMetadata.Status.TERMINATED
          if marketMetadata.getMarketId.entityId == entityId =>
        log.info(
          s"this actor:${self.path} will be to stoped, due to the status of this market has been changed to TERMINATED."
        )
        context.stop(self)
      case _ => //READONLY的不处理，需要能继续可以查询得到orderbook
    }
  }

}
