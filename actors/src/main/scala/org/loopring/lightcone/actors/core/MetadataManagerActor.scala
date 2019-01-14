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
import akka.cluster.singleton.{
  ClusterSingletonManager,
  ClusterSingletonManagerSettings,
  ClusterSingletonProxy,
  ClusterSingletonProxySettings
}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent.{ExecutionContext, Future}

// Owner: Yongfeng
object MetadataManagerActor {
  val name = "metadata_manager"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      dbModule: DatabaseModule,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new MetadataManagerActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      MetadataManagerActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${MetadataManagerActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${MetadataManagerActor.name}_proxy"
    )
  }
}

class MetadataManagerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(OrderCutoffHandlerActor.name)
    with ActorLogging {

  def receive: Receive = {

    case req: SaveTokenMetadatas.Req =>
      dbModule.tokenMetadataService
        .saveTokens(req.tokens)
        .map(SaveTokenMetadatas.Res(_))

    case req: UpdateTokenMetadata.Req =>
      dbModule.tokenMetadataService
        .updateToken(req.token.get)
        .map(UpdateTokenMetadata.Res(_))

    case req: UpdateTokenBurnRate.Req =>
      dbModule.tokenMetadataService
        .updateBurnRate(req.address, req.burnRate)
        .map(UpdateTokenBurnRate.Res(_))

    case req: DisableToken.Req =>
      dbModule.tokenMetadataService
        .disableToken(req.address)
        .map(DisableToken.Res(_))

    case req: SaveMarketMetadatas.Req =>
      dbModule.marketMetadataService
        .saveMarkets(req.markets)
        .map(SaveMarketMetadatas.Res(_))

    case req: UpdateMarketMetadata.Req =>
      dbModule.marketMetadataService
        .updateMarket(req.market.get)
        .map(UpdateMarketMetadata.Res(_))

    case req: DisableMarket.Req =>
      req.market match {
        case DisableMarket.Req.Market.MarketHash(value) =>
          dbModule.marketMetadataService
            .disableMarketByHash(value)
            .map(DisableMarket.Res(_))
        case DisableMarket.Req.Market.MarketId(value) =>
          dbModule.marketMetadataService
            .disableMarketById(value)
            .map(DisableMarket.Res(_))
        case DisableMarket.Req.Market.Empty =>
          throw ErrorException(
            ErrorCode.ERR_INVALID_ARGUMENT,
            "Parameter market could not be empty"
          )
      }
  }
}
