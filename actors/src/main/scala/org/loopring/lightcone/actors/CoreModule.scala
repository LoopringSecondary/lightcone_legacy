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
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.persistence.OrdersDalActor
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.persistence.base.BaseDatabaseModule
import org.loopring.lightcone.persistence.dals.OrderDalImpl
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.proto.core.{ XMarketManagerConfig, XOrderbookConfig }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class CoreModule(config: Config)
  extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    implicit val system = ActorSystem("Lightcone", config)
    implicit val ec = system.dispatcher
    implicit val cluster = Cluster(system)
    implicit val timeout = Timeout(5 second)

    bind[Config].toInstance(config)

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

    //-----------deploy actors-----------
    //启动时都需要 TokenMetadataSyncActor
    actors.add(
      TokenMetadataSyncActor.name,
      system.actorOf(Props(new TokenMetadataSyncActor()), TokenMetadataSyncActor.name)
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

    val orderbookConfig = XOrderbookConfig(
      levels = 2,
      priceDecimals = 5,
      precisionForAmount = 2,
      precisionForTotal = 1
    )
    actors.add(
      OrderbookManagerActor.name,
      system.actorOf(Props(new OrderbookManagerActor(orderbookConfig)), OrderbookManagerActor.name)
    )

    actors.add(
      AccountBalanceActor.name,
      system.actorOf(Props(new AccountBalanceActor()), AccountBalanceActor.name)
    )

    val dal = new OrderDalImpl()

    actors.add(
      OrdersDalActor.name,
      system.actorOf(Props(new OrdersDalActor(dal)), OrdersDalActor.name)
    )

    actors.add(
      OrderHistoryActor.name,
      system.actorOf(Props(new OrderHistoryActor()), OrderHistoryActor.name)
    )

  }
}
