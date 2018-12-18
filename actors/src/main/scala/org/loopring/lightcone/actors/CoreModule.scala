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
import com.google.inject.AbstractModule
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.utils._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.duration._

class CoreModule(config: Config) extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    implicit val system = ActorSystem("Lightcone", config)
    implicit val cluster = Cluster(system)
    implicit val materializer = ActorMaterializer()(system)
    implicit val timeout = Timeout(2 second)
    implicit val ec = system.dispatcher
    implicit val c_ = config

    bind[Config].toInstance(config)
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(cluster)
    bind[ActorMaterializer].toInstance(materializer)
    bind[Timeout].toInstance(timeout)

    bind[ExecutionContextExecutor].toInstance(system.dispatcher)
    bind[ExecutionContext].toInstance(system.dispatcher)
    bind[ExecutionContext]
      .annotatedWithName("db-execution-context")
      .toInstance(system.dispatchers.lookup("db-execution-context"))

    implicit val actors = new MapBasedLookup[ActorRef]()
    bind[Lookup[ActorRef]].toInstance(actors)

    implicit val timeProvider: TimeProvider = new SystemTimeProvider()
    bind[TimeProvider].toInstance(timeProvider)

    implicit val dbConfig: DatabaseConfig[JdbcProfile] =
      DatabaseConfig.forConfig("db.default", config)
    bind[DatabaseConfig[JdbcProfile]].toInstance(dbConfig)

    implicit val dbModule = new DatabaseModule()
    bind[DatabaseModule].toInstance(dbModule)
    dbModule.createTables()

    implicit val tmm = new TokenMetadataManager()
    bind[TokenMetadataManager].toInstance(tmm)

    implicit val tokenValueEstimator: TokenValueEstimator =
      new TokenValueEstimator()
    bind[TokenValueEstimator].toInstance(tokenValueEstimator)

    implicit val dustEvaluator: DustOrderEvaluator = new DustOrderEvaluator()
    bind[DustOrderEvaluator].toInstance(dustEvaluator)

    implicit val ringIncomeEstimator: RingIncomeEstimator =
      new RingIncomeEstimatorImpl()
    bind[RingIncomeEstimator].toInstance(ringIncomeEstimator)

    //-----------deploy local actors-----------
    val listener =
      system.actorOf(Props[BadMessageListener], "bad_message_listener")

    system.eventStream.subscribe(listener, classOf[UnhandledMessage])
    system.eventStream.subscribe(listener, classOf[DeadLetter])

    //-----------deploy cluster singletons-----------
    actors.add(
      OrderRecoverCoordinator.name,
      system.actorOf(
        ClusterSingletonProxy.props(
          singletonManagerPath = OrderRecoverCoordinator.name,
          settings = ClusterSingletonProxySettings(system)
        ),
        name = OrderRecoverCoordinator.name
      )
    )

    // This actor must be deployed on every node for TokenMetadataManager
    actors.add(
      TokenMetadataRefresher.name,
      system
        .actorOf(Props(new TokenMetadataRefresher), TokenMetadataRefresher.name)
    )

    //-----------deploy sharded actors-----------
    actors.add(EthereumQueryActor.name, EthereumQueryActor.startShardRegion)

    actors.add(
      MultiAccountManagerActor.name,
      MultiAccountManagerActor.startShardRegion
    )
    actors.add(DatabaseQueryActor.name, DatabaseQueryActor.startShardRegion)
    actors.add(
      EthereumEventExtractorActor.name,
      EthereumEventExtractorActor.startShardRegion
    )
    actors.add(
      EthereumEventPersistorActor.name,
      EthereumEventPersistorActor.startShardRegion
    )
    actors.add(GasPriceActor.name, GasPriceActor.startShardRegion)
    actors.add(MarketManagerActor.name, MarketManagerActor.startShardRegion)
    actors.add(
      OrderbookManagerActor.name,
      OrderbookManagerActor.startShardRegion
    )
    actors.add(OrderHandlerActor.name, OrderHandlerActor.startShardRegion)
    actors.add(OrderRecoverActor.name, OrderRecoverActor.startShardRegion)
    actors.add(RingSettlementActor.name, RingSettlementActor.startShardRegion)
    actors.add(EthereumAccessActor.name, EthereumAccessActor.startShardRegion)

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
      MarketManagerMessageValidator.name,
      MessageValidationActor(
        new MarketManagerMessageValidator(),
        MarketManagerActor.name,
        MarketManagerMessageValidator.name
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

    actors.add(
      EntryPointActor.name,
      system.actorOf(Props(new EntryPointActor()), EntryPointActor.name)
    )

    //-----------deploy JSONRPC service-----------

    if (cluster.selfRoles.contains("jsonrpc")) {
      val server = new JsonRpcServer(config, actors.get(EntryPointActor.name))
      with RpcBinding
      server.start()
    }
  }
}
