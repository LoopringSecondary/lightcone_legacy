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
import akka.util.Timeout
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.persistence._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto.core._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class CoreModule(config: Config)
  extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    implicit val system = ActorSystem("Lightcone", config)
    implicit val cluster = Cluster(system)
    implicit val timeout = Timeout(2 second)
    implicit val ec = system.dispatcher

    bind[Config].toInstance(config)
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(cluster)
    bind[Timeout].toInstance(timeout)

    bind(classOf[ExecutionContext]).toInstance(global)
    bind(classOf[ExecutionContext]).annotatedWith(Names.named("db-execution-context")).toInstance(global)

    implicit val dbConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig("db.default", config)
    bind[DatabaseConfig[JdbcProfile]].toInstance(dbConfig)

    implicit val tmm = new TokenMetadataManager()
    bind[TokenMetadataManager].toInstance(tmm)

    implicit val tokenValueEstimator: TokenValueEstimator = new TokenValueEstimator()
    bind[TokenValueEstimator].toInstance(tokenValueEstimator)

    implicit val dustEvaluator: DustOrderEvaluator = new DustOrderEvaluator()
    bind[DustOrderEvaluator].toInstance(dustEvaluator)

    implicit val actors = new MapBasedLookup[ActorRef]()
    bind[Lookup[ActorRef]].toInstance(actors)

    implicit val ringIncomeEstimator: RingIncomeEstimator = new RingIncomeEstimatorImpl()
    bind[RingIncomeEstimator].toInstance(ringIncomeEstimator)

    implicit val timeProvider: TimeProvider = new SystemTimeProvider()
    bind[TimeProvider].toInstance(timeProvider)

    implicit val dbModule = new DatabaseModule()
    bind[DatabaseModule].toInstance(dbModule)

    //-----------deploy actors-----------
    actors.add(
      TokenMetadataActor.name,
      TokenMetadataActor.startShardRegion
    )

    actors.add(
      AccountManagerActor.name,
      AccountManagerActor.startShardRegion(100, true)
    )

    val marketsConfig = XMarketManagerConfig()
    actors.add(
      MarketManagerActor.name,
      MarketManagerActor.startShardRegion(marketsConfig, true)
    )

    actors.add(
      AccountBalanceActor.name,
      AccountBalanceActor.startShardRegion
    )

    actors.add(
      EthereumAccessActor.name,
      EthereumAccessActor.startShardRegion
    )

    val orderbookConfig = XOrderbookConfig(
      levels = 2,
      priceDecimals = 5,
      precisionForAmount = 2,
      precisionForTotal = 1
    )
    actors.add(
      OrderbookManagerActor.name,
      OrderbookManagerActor.startShardRegion(orderbookConfig)
    )

    actors.add(
      OrderHistoryActor.name,
      OrderHistoryActor.startShardRegion
    )

    actors.add(
      RingSettlementActor.name,
      RingSettlementActor.startShardRegion("xyz")
    )

    actors.add(
      EntryPointActor.name,
      system.actorOf(Props(new EntryPointActor()), EntryPointActor.name)
    )
  }
}
