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
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.core.XMarketManagerConfig
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

    //同时需要启动actor并开始同步，
    implicit val tmm = new TokenMetadataManager()
    bind[TokenMetadataManager].toInstance(tmm)

    implicit val tokenValueEstimator: TokenValueEstimator = new TokenValueEstimator()
    implicit val dustEvaluator: DustOrderEvaluator = new DustOrderEvaluator()
    val actors = new MapBasedLookup[ActorRef]()
    bind[Lookup[ActorRef]].toInstance(actors)

    implicit val ringIncomeEstimator = new RingIncomeEstimatorImpl()
    implicit val timeProvider = new SystemTimeProvider()


    //-----------deploy actors-----------
    //启动时都需要 TokenMetadataSyncActor
    system.actorOf(Props[TokenMetadataSyncActor], TokenMetadataSyncActor.name)
    deployCoreAccountManager(actors, 100, false)
    val marketsConfig = XMarketManagerConfig()
    deployCoreMarketManager(actors, marketsConfig, false)
  }

  def deployCoreAccountManager(
    actors: Lookup[ActorRef],
    recoverBatchSize: Int,
    skipRecovery: Boolean = false
  )(
    implicit
    system: ActorSystem,
    ec: ExecutionContext,
    timeout: Timeout,
    dustEvaluator: DustOrderEvaluator
  ): ActorRef = {
    AccountManagerActor.createShardActor(actors, recoverBatchSize, skipRecovery)
  }

  def deployCoreMarketManager(
    actors: Lookup[ActorRef],
    config: XMarketManagerConfig,
    skipRecovery: Boolean = false
  )(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    timeProvider: TimeProvider,
    tokenValueEstimator: TokenValueEstimator,
    ringIncomeEstimator: RingIncomeEstimator,
    dustOrderEvaluator: DustOrderEvaluator,
    tokenMetadataManager: TokenMetadataManager,
    system: ActorSystem
  ): ActorRef = {
    MarketManagerActor.createShardActor(actors, config, skipRecovery)
  }

}
