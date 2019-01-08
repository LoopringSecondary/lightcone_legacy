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

package org.loopring.lightcone.actors

import akka.actor._
import akka.cluster._
import akka.cluster.singleton._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject._
import com.google.inject.name.Named
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer
import org.loopring.lightcone.actors.utils._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.slf4s.Logging
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class ClusterDeployer @Inject()(
    implicit
    @Named("ignore-cluster-roles") deployActorsIgnoringRoles: Boolean,
    actors: Lookup[ActorRef],
    actorMaterializer: ActorMaterializer,
    brb: EthereumBatchCallRequestBuilder,
    cluster: Cluster,
    config: Config,
    dbModule: DatabaseModule,
    dustOrderEvaluator: DustOrderEvaluator,
    ec: ExecutionContext,
    ece: ExecutionContextExecutor,
    rb: EthereumCallRequestBuilder,
    rie: RingIncomeEvaluator,
    supportedMarkets: SupportedMarkets,
    timeProvider: TimeProvider,
    timeout: Timeout,
    tokenManager: TokenManager,
    tve: TokenValueEvaluator,
    system: ActorSystem)
    extends Object
    with Logging {

  def deploy() {
    // bind[DatabaseModule].in[Singleton]
    // dbModule.createTables()

    //-----------deploy local actors-----------
    val listener =
      system.actorOf(Props[BadMessageListener], BadMessageListener.name)

    system.eventStream.subscribe(listener, classOf[UnhandledMessage])
    system.eventStream.subscribe(listener, classOf[DeadLetter])

    Cluster(system).registerOnMemberUp {
      val listener =
        system.actorOf(Props[BadMessageListener], "bad_message_listener")

      system.eventStream.subscribe(listener, classOf[UnhandledMessage])
      system.eventStream.subscribe(listener, classOf[DeadLetter])

      actors.add(TokenMetadataRefresher.name, TokenMetadataRefresher.start)

      //-----------deploy sharded actors-----------
      actors.add(EthereumQueryActor.name, EthereumQueryActor.start)
      actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)
      actors.add(GasPriceActor.name, GasPriceActor.start)
      actors.add(MarketManagerActor.name, MarketManagerActor.start)
      actors.add(OrderPersistenceActor.name, OrderPersistenceActor.start)
      actors.add(OrderRecoverActor.name, OrderRecoverActor.start)
      actors.add(MultiAccountManagerActor.name, MultiAccountManagerActor.start)

      actors.add(
        EthereumEventExtractorActor.name,
        EthereumEventExtractorActor.start
      )
      actors.add(
        EthereumEventPersistorActor.name,
        EthereumEventPersistorActor.start
      )

      actors.add(OrderbookManagerActor.name, OrderbookManagerActor.start)

      //-----------deploy singleton actors-----------
      actors.add(EthereumAccessActor.name, EthereumAccessActor.start)

      actors.add(OrderRecoverCoordinator.name, OrderRecoverCoordinator.start)

      actors.add(OrderStatusMonitorActor.name, OrderStatusMonitorActor.start)

      actors.add(EthereumClientMonitor.name, EthereumClientMonitor.start)
      actors.add(
        RingSettlementManagerActor.name,
        RingSettlementManagerActor.start
      )

      //-----------deploy local actors-----------
      actors.add(
        MultiAccountManagerMessageValidator.name,
        MessageValidationActor(
          new MultiAccountManagerMessageValidator(),
          MultiAccountManagerActor.name,
          MultiAccountManagerMessageValidator.name
        )
      )

      actors.add(
        DatabaseQueryMessageValidator.name,
        MessageValidationActor(
          new DatabaseQueryMessageValidator(),
          DatabaseQueryActor.name,
          DatabaseQueryMessageValidator.name
        )
      )

      actors.add(
        EthereumQueryMessageValidator.name,
        MessageValidationActor(
          new EthereumQueryMessageValidator(),
          EthereumQueryActor.name,
          EthereumQueryMessageValidator.name
        )
      )

      actors.add(
        OrderbookManagerMessageValidator.name,
        MessageValidationActor(
          new OrderbookManagerMessageValidator(),
          OrderbookManagerActor.name,
          OrderbookManagerMessageValidator.name
        )
      )

      //-----------deploy local actors that depend on cluster aware actors-----------
      actors.add(EntryPointActor.name, EntryPointActor.start)

      //-----------deploy JSONRPC service-----------
      if (cluster.selfRoles.contains("jsonrpc")) {
        val server = new JsonRpcServer(config, actors.get(EntryPointActor.name))
        with RpcBinding
        server.start
      }

    }
  }
}
