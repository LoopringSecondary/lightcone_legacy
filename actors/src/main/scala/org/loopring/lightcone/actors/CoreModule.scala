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
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer
import org.loopring.lightcone.actors.utils._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.actors.validator._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class CoreModule(config: Config) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {

    // TODO(read from config)
    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(0.0)

    val system = ActorSystem("Lightcone", config)

    bind[Config].toInstance(config)
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(Cluster(system))
    bind[ActorMaterializer].toInstance(ActorMaterializer()(system))

    bind[Timeout].toInstance(Timeout(2.second))
    bind[TimeProvider].to[SystemTimeProvider]

    bind[EthereumCallRequestBuilder]
    bind[EthereumBatchCallRequestBuilder]

    bind[ExecutionContextExecutor].toInstance(system.dispatcher)
    bind[ExecutionContext].toInstance(system.dispatcher)
    bind[ExecutionContext]
      .annotatedWithName("db-execution-context")
      .toInstance(system.dispatchers.lookup("db-execution-context"))

    bind[SupportedMarkets].toInstance(SupportedMarkets(config))
    bind[Lookup[ActorRef]].toInstance(new MapBasedLookup[ActorRef]())

    // val dbConfig: DatabaseConfig[JdbcProfile] =
    //   DatabaseConfig.forConfig("db.default", config)
    // bind[DatabaseConfig[JdbcProfile]].toInstance(dbConfig)

    bind[DatabaseModule].in[Singleton]
    bind[TokenManager].in[Singleton]

    bind[TokenValueEstimator]
    bind[DustOrderEvaluator]
    bind[RingIncomeEstimator].to[RingIncomeEstimatorImpl]

    bind[TokenMetadataRefresher]
    bind[EthereumEventExtractorActor]
    bind[EthereumQueryActor]
    bind[DatabaseQueryActor]

    // Cluster(system).registerOnMemberUp {

    // actors.add(GasPriceActor.name, GasPriceActor.start)
    // actors.add(MarketManagerActor.name, MarketManagerActor.start)
    // actors.add(
    //   OrderPersistenceActor.name,
    //   OrderPersistenceActor.start
    // )
    // actors.add(OrderRecoverActor.name, OrderRecoverActor.start)
    // actors.add(
    //   MultiAccountManagerActor.name,
    //   MultiAccountManagerActor.start
    // )

    // actors.add(
    //   EthereumEventExtractorActor.name,
    //   EthereumEventExtractorActor.start
    // )
    // actors.add(
    //   EthereumEventPersistorActor.name,
    //   EthereumEventPersistorActor.start
    // )

    // actors.add(
    //   OrderbookManagerActor.name,
    //   OrderbookManagerActor.start
    // )

    // //-----------deploy singleton actors-----------
    // actors.add(EthereumAccessActor.name, EthereumAccessActor.start)

    // actors.add(
    //   OrderRecoverCoordinator.name,
    //   OrderRecoverCoordinator.start
    // )

    // actors.add(
    //   OrderStatusMonitorActor.name,
    //   OrderStatusMonitorActor.start
    // )

    // actors.add(
    //   EthereumClientMonitor.name,
    //   EthereumClientMonitor.start
    // )
    // actors.add(
    //   RingSettlementManagerActor.name,
    //   RingSettlementManagerActor.start
    // )

    // //-----------deploy local actors-----------
    // actors.add(
    //   MultiAccountManagerMessageValidator.name,
    //   MessageValidationActor(
    //     new MultiAccountManagerMessageValidator(),
    //     MultiAccountManagerActor.name,
    //     MultiAccountManagerMessageValidator.name
    //   )
    // )

    // actors.add(
    //   DatabaseQueryMessageValidator.name,
    //   MessageValidationActor(
    //     new DatabaseQueryMessageValidator(),
    //     DatabaseQueryActor.name,
    //     DatabaseQueryMessageValidator.name
    //   )
    // )

    // actors.add(
    //   EthereumQueryMessageValidator.name,
    //   MessageValidationActor(
    //     new EthereumQueryMessageValidator(),
    //     EthereumQueryActor.name,
    //     EthereumQueryMessageValidator.name
    //   )
    // )

    // actors.add(
    //   OrderbookManagerMessageValidator.name,
    //   MessageValidationActor(
    //     new OrderbookManagerMessageValidator(),
    //     OrderbookManagerActor.name,
    //     OrderbookManagerMessageValidator.name
    //   )
    // )

    // //-----------deploy local actors that depend on cluster aware actors-----------
    // actors.add(EntryPointActor.name, EntryPointActor.start)

    // //-----------deploy JSONRPC service-----------
    // if (cluster.selfRoles.contains("jsonrpc")) {
    //   val server = new JsonRpcServer(config, actors.get(EntryPointActor.name))
    //   with RpcBinding
    //   server.start
    // }
    // }
  }
}
