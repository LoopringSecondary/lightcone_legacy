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
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import org.slf4s.Logging

// Owner: Daniel
class CoreModule(config: Config)
    extends AbstractModule
    with ScalaModule
    with Logging {

  private var dbConfigMap = Map.empty[String, DatabaseConfig[JdbcProfile]]

  override def configure(): Unit = {

    val system = ActorSystem("Lightcone", config)

    bind[Config].toInstance(config)
    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(Cluster(system))
    bind[ActorMaterializer].toInstance(ActorMaterializer()(system))

    bind[ExecutionContextExecutor].toInstance(system.dispatcher)
    bind[ExecutionContext].toInstance(system.dispatcher)
    bind[ExecutionContext]
      .annotatedWithName("db-execution-context")
      .toInstance(system.dispatchers.lookup("db-execution-context"))

    // --- bind db configs ---------------------
    bindDatabaseConfigProviderForNames(
      "dbconfig-dal-token-metadata",
      "dbconfig-dal-order",
      "dbconfig-dal-trade",
      "dbconfig-dal-token-balance",
      "dbconfig-dal-block",
      "dbconfig-dal-settlement-tx",
      "dbconfig-dal-order-status-monitor"
    )

    // --- bind dals ---------------------
    bind[TokenMetadataDal].to[TokenMetadataDalImpl].in[Singleton]
    bind[OrderDal].to[OrderDalImpl].in[Singleton]
    bind[TradeDal].to[TradeDalImpl].in[Singleton]
    bind[BlockDal].to[BlockDalImpl].in[Singleton]
    bind[SettlementTxDal].to[SettlementTxDalImpl].in[Singleton]
    bind[OrderStatusMonitorDal].to[OrderStatusMonitorDalImpl].in[Singleton]

    // --- bind db services ---------------------
    bind[OrderService].to[OrderServiceImpl].in[Singleton]
    bind[TokenMetadataService].to[TokenMetadataServiceImpl].in[Singleton]
    bind[TradeService].to[TradeServiceImpl].in[Singleton]
    bind[SettlementTxService].to[SettlementTxServiceImpl].in[Singleton]
    bind[OrderStatusMonitorService]
      .to[OrderStatusMonitorServiceImpl]
      .in[Singleton]

    // --- bind local singletons ---------------------
    bind[DatabaseModule].in[Singleton]
    bind[TokenManager].in[Singleton]

    bind[SupportedMarkets].toInstance(SupportedMarkets(config))
    bind[Lookup[ActorRef]].toInstance(new MapBasedLookup[ActorRef]())

    // --- bind other classes ---------------------
    bind[TimeProvider].to[SystemTimeProvider]
    bind[EthereumCallRequestBuilder]
    bind[EthereumBatchCallRequestBuilder]

    bind[TokenValueEvaluator]
    bind[DustOrderEvaluator]
    bind[RingIncomeEvaluator].to[RingIncomeEvaluatorImpl]

    // --- bind primative types ---------------------
    bind[Timeout].toInstance(Timeout(2.second))

    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(config.getDouble("relay.dust-order-threshold"))

    bind[Boolean]
      .annotatedWithName("deploy-actors-ignoring-roles")
      .toInstance(false)
  }

  private def bindDatabaseConfigProviderForNames(names: String*) = {
    bind[DatabaseConfig[JdbcProfile]]
      .toProvider(new Provider[DatabaseConfig[JdbcProfile]] {
        def get() = getDbConfigByKey("db.default")
      })

    names.foreach { name =>
      bind[DatabaseConfig[JdbcProfile]]
        .annotatedWithName(name)
        .toProvider(new Provider[DatabaseConfig[JdbcProfile]] {
          def get() = getDbConfigByKey(s"db.${name}")
        })
    }
  }

  private def getDbConfigByKey(key: String) = {
    dbConfigMap.get(key) match {
      case None =>
        val dbConfig: DatabaseConfig[JdbcProfile] =
          DatabaseConfig.forConfig(key, config)
        log.info(s"creating DatabaseConfig instance for config key: $key")
        dbConfigMap += key -> dbConfig
        dbConfig
      case Some(dbConfig) =>
        dbConfig
    }
  }
}
