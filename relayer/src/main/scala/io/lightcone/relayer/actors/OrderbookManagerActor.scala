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
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.Config
import io.lightcone.core.ErrorCode.ERR_INTERNAL_UNKNOWN
import io.lightcone.relayer.base._
import io.lightcone.lib._
import io.lightcone.relayer.data._
import io.lightcone.core._
import org.slf4s.Logging
import scala.concurrent._

// Owner: Hongyu
object OrderbookManagerActor extends DeployedAsShardedByMarket with Logging {
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
  val extractShardingObject: PartialFunction[Any, MarketPair] = {
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

  import MarketMetadata.Status._

  val selfConfig = config.getConfig(OrderbookManagerActor.name)
  val orderbookRecoverSize = selfConfig.getInt("orderbook-recover-size")
  val refreshIntervalInSeconds = selfConfig.getInt("refresh-interval-seconds")
  val initialDelayInSeconds = selfConfig.getInt("initial-delay-in-seconds")

  val marketPair = metadataManager
    .getMarkets(ACTIVE, READONLY)
    .find(
      m =>
        OrderbookManagerActor
          .getEntityId(m.getMetadata.marketPair.get) == entityId
    )
    .map(_.getMetadata.marketPair.get)
    .getOrElse {
      val error = s"unable to find market pair matching entity id ${entityId}"
      log.error(error)
      throw new IllegalStateException(error)
    }

  def marketMetadata =
    metadataManager
      .getMarket(marketPair)
      .metadata
      .getOrElse(
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          s"not found metadata with marketPair:$marketPair"
        )
      )
  val marketPairHashedValue = OrderbookManagerActor.getEntityId(marketPair)
  val manager: OrderbookManager = new OrderbookManagerImpl(marketMetadata)
  @inline def marketManagerActor = actors.get(MarketManagerActor.name)

  val repeatedJobs = Seq(
    Job(
      name = "load_orderbook_from_market",
      dalayInSeconds = refreshIntervalInSeconds,
      initialDalayInSeconds = initialDelayInSeconds,
      run = () => syncOrderbookFromMarket()
    )
  )

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
        Option(metadataManager.getMarket(marketPair).getMetadata)
      } catch {
        case _: Throwable => None
      }
      metadataOpt match {
        case None =>
          log.warning("I'm stopping myself as the market metadata is not found")
          context.system.stop(self)
        case Some(metadata) if metadata.status.isTerminated =>
          log.warning(
            s"I'm stopping myself as the market is terminiated: $metadata"
          )
          context.system.stop(self)
        case Some(metadata) =>
          log.info(
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
