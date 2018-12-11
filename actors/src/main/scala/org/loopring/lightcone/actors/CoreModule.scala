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
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.utils._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto.core._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class CoreModule(config: Config)
  extends AbstractModule with ScalaModule {

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
    bind[ExecutionContext].annotatedWithName("db-execution-context")
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

    implicit val tmm = new TokenMetadataManager()
    bind[TokenMetadataManager].toInstance(tmm)

    // This actor must be deployed on every node for TokenMetadataManager
    val refresher = system.actorOf(Props(new TokenMetadataRefresher), "token_metadata_refresher")

    implicit val tokenValueEstimator: TokenValueEstimator = new TokenValueEstimator()
    bind[TokenValueEstimator].toInstance(tokenValueEstimator)

    implicit val dustEvaluator: DustOrderEvaluator = new DustOrderEvaluator()
    bind[DustOrderEvaluator].toInstance(dustEvaluator)

    implicit val ringIncomeEstimator: RingIncomeEstimator = new RingIncomeEstimatorImpl()
    bind[RingIncomeEstimator].toInstance(ringIncomeEstimator)

    //-----------deploy actors-----------
    actors.add(AccountBalanceActor.name, AccountBalanceActor.startShardRegion)
    actors.add(AccountManagerActor.name, AccountManagerActor.startShardRegion)
    actors.add(DatabaseQueryActor.name, DatabaseQueryActor.startShardRegion)
    actors.add(EthereumEventExtractorActor.name, EthereumEventExtractorActor.startShardRegion)
    actors.add(EthereumEventPersistorActor.name, EthereumEventPersistorActor.startShardRegion)
    actors.add(GasPriceActor.name, GasPriceActor.startShardRegion)
    actors.add(MarketManagerActor.name, MarketManagerActor.startShardRegion)
    actors.add(OrderbookManagerActor.name, OrderbookManagerActor.startShardRegion)
    actors.add(OrderHandlerActor.name, OrderHandlerActor.startShardRegion)
    actors.add(OrderHistoryActor.name, OrderHistoryActor.startShardRegion)
    actors.add(OrderRecoverActor.name, OrderRecoverActor.startShardRegion)
    actors.add(RingSettlementActor.name, RingSettlementActor.startShardRegion)
    actors.add(EthereumAccessActor.name, EthereumAccessActor.startShardRegion)

    actors.add(
      EntryPointActor.name,
      system.actorOf(Props(new EntryPointActor()), EntryPointActor.name)
    )

    val listener = system.actorOf(Props[BadMessageListener], "bad_message_listener")
    system.eventStream.subscribe(listener, classOf[UnhandledMessage])
    system.eventStream.subscribe(listener, classOf[DeadLetter])
  }
}
