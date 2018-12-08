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
import org.loopring.lightcone.actors.persistence.{ OrderStateActor, OrdersDalActor }
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.persistence.dals.OrderDalImpl
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

    //todo: test docker
    system.actorOf(Props[MyActor], "myactor")
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
    system.actorOf(Props(new TokenMetadataSyncActor()), TokenMetadataSyncActor.name)
    val accountManagerShardActor = deployCoreAccountManager(actors, 100, true)
    val marketsConfig = XMarketManagerConfig()
    val marketManagerShardActor = deployCoreMarketManager(actors, marketsConfig, true)

    actors.add(AccountManagerActor.name, accountManagerShardActor)
    actors.add(MarketManagerActor.name, marketManagerShardActor)

    val orderbookConfig = XOrderbookConfig(
      levels = 2,
      priceDecimals = 5,
      precisionForAmount = 2,
      precisionForTotal = 1
    )
    val orderbookManagerActor = system.actorOf(
      Props(new OrderbookManagerActor(orderbookConfig)),
      OrderbookManagerActor.name
    )
    actors.add(OrderbookManagerActor.name, orderbookManagerActor)

    val accountBalanceActor = system.actorOf(Props(new AccountBalanceActor()), AccountBalanceActor.name)
    actors.add(AccountBalanceActor.name, accountBalanceActor)
    val orderStateActor = system.actorOf(Props(new OrderStateActor()), OrderStateActor.name)
    actors.add(OrderStateActor.name, orderStateActor)
    val dal = new OrderDalImpl()
    val orderDalActor = system.actorOf(Props(new OrdersDalActor(dal)), OrdersDalActor.name)
    actors.add(OrdersDalActor.name, orderDalActor)

    println(s"#### accountmanager ${accountManagerShardActor.path.address.toString}")
    println(s"#### orderbookManagerActor ${cluster.selfRoles}${orderbookManagerActor}, ${orderbookManagerActor.path.toString}")

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
