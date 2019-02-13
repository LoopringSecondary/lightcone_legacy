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

  val mediator = DistributedPubSub(context.system).mediator

  def orderbookManagerActor = actors.get(OrderbookManagerActor.name)

  def marketManagerActor = actors.get(MarketManagerActor.name)

  def multiAccountManagerActor = actors.get(MultiAccountManagerActor.name)

  val numsOfAccountShards = config.getInt("multi_account_manager.num-of-shards")

  private var tokens = Seq.empty[TokenMetadata]
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
        actors1 <- toNotifyActors()
        _ = actors1 foreach { actor =>
          actor ! req
        }
      } yield Unit

    case _: GetMetadatas.Req =>
      sender ! GetMetadatas.Res(tokens = tokens, markets = markets)
  }

  private def refreshMetadata() =
    for {
      tokens_ <- (metadataManagerActor ? LoadTokenMetadata.Req())
        .mapTo[LoadTokenMetadata.Res]
        .map(_.tokens)
      markets_ <- (metadataManagerActor ? LoadMarketMetadata.Req())
        .mapTo[LoadMarketMetadata.Res]
        .map(_.markets)
    } yield {
      assert(tokens_.nonEmpty)
      assert(markets_.nonEmpty)
      tokens = tokens_.map(MetadataManager.normalizeToken)
      markets = markets_.map(MetadataManager.normalizeMarket)
      metadataManager.reset(tokens_, markets_)
    }

  //TODO：经过简单的测试，但是仍要在集群环境下确认只会获取本地的actor
  //文档：https://doc.akka.io/docs/akka/2.5/general/addressing.html#actor-path-anchors
  //TODO(hongyu): 测试LocalActor的方法
  private def toNotifyActors() = {
    for {
      accountManagerActors <- Future.sequence {
        (0 until numsOfAccountShards) map { i =>
          getLocalActorRef(
            s"akka://${context.system.name}/system/sharding/" +
              s"${MultiAccountManagerActor.name}/${i}/${MultiAccountManagerActor.name}_${i}"
          )
        }
      }
      marketOrOrderbookManagerActors <- Future.sequence {
        metadataManager.getValidMarketPairs flatMap {
          case (_, marketId) =>
            val entityId = MarketManagerActor.getEntityId(marketId)
            val orderbookActor = getLocalActorRef(
              s"akka://${context.system.name}/system/sharding/" +
                s"${OrderbookManagerActor.name}/${OrderbookManagerActor.name}_${entityId}/${OrderbookManagerActor.name}_${entityId}"
            )
            val marketActor = getLocalActorRef(
              s"akka://${context.system.name}/system/sharding/" +
                s"${MarketManagerActor.name}/${MarketManagerActor.name}_${entityId}/${MarketManagerActor.name}_${entityId}"
            )
            Seq(orderbookActor, marketActor)
        }
      }
    } yield
      (accountManagerActors ++ marketOrOrderbookManagerActors)
        .filterNot(_ == null)
  }

  private def getLocalActorRef(path: String): Future[ActorRef] = {
    context.system
      .actorSelection(path)
      .resolveOne()
      .recover {
        case _: Throwable => null
      }
  }

}
