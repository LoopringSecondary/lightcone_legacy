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

package io.lightcone.relayer.integration
import akka.actor.{ActorRef, ActorSystem}
import io.lightcone.core.MarketPair
import io.lightcone.relayer.actors.{MarketManagerActor, OrderbookManagerActor}

import scala.concurrent.Future

trait MetadataSpecHelper {
  me: CommonHelper =>

  val metadataRefresherInterval =
    system.settings.config.getInt("metadata_manager.refresh-interval-seconds")

  protected def isActorAlive(
      system: ActorSystem,
      market: MarketPair
    ): Future[Boolean] = {
    val entityId = MarketManagerActor.getEntityId(market)
    val numsOfMarketManagerShards =
      system.settings.config.getInt("market_manager.num-of-shards")
    val numsOfOrderbookManagerShards =
      system.settings.config.getInt("orderbook_manager.num-of-shards")

    val orderbookEntityId = s"${OrderbookManagerActor.name}_${entityId}"
    val marketManagerEntityId = s"${MarketManagerActor.name}_${entityId}"

    val marketManagerPath = s"akka://${system.name}/system/sharding/" +
      s"${MarketManagerActor.name}/" +
      s"${(math.abs(marketManagerEntityId.hashCode) % numsOfMarketManagerShards).toString}/" +
      s"${marketManagerEntityId}"
    val orderbookManagerPath = s"akka://${system.name}/system/sharding/" +
      s"${OrderbookManagerActor.name}/" +
      s"${(math.abs(orderbookEntityId.hashCode) % numsOfOrderbookManagerShards).toString}/" +
      s"${orderbookEntityId}"

    for {
      marketManagerActor <- getLocalActorRef(system, marketManagerPath)
      orderbookManagerActor <- getLocalActorRef(system, orderbookManagerPath)
    } yield {
      log.info(
        s"--- isActorAlive --- ${marketManagerPath} -> ${marketManagerActor}, ${orderbookManagerPath} -> ${orderbookManagerActor}"
      )
      marketManagerActor != null && orderbookManagerActor != null
    }

  }

  protected def getLocalActorRef(
      system: ActorSystem,
      path: String
    ): Future[ActorRef] = {
    system
      .actorSelection(path)
      .resolveOne()
      .recover {
        case _: Throwable => null
      }
  }

}
