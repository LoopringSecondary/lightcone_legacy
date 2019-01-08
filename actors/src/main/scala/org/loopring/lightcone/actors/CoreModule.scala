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
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class CoreModule(config: Config) extends AbstractModule with ScalaModule {

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
    // TODO(yongfeng): use different config for different dals
    bindDBForNames(
      DatabaseConfig.forConfig("db.default", config),
      Seq(
        "dbconfig-dal-token-metadata",
        "dbconfig-dal-order",
        "dbconfig-dal-trade",
        "dbconfig-dal-token-balance",
        "dbconfig-dal-block",
        "dbconfig-dal-settlement-tx",
        "dbconfig-dal-order-status-monitor"))

    // --- bind dals ---------------------
    bind[TokenMetadataDal].to[TokenMetadataDalImpl].in[Singleton]
    bind[OrderDal].to[OrderDalImpl].in[Singleton]
    bind[TradeDal].to[TradeDalImpl].in[Singleton]
    bind[TokenBalanceDal].to[TokenBalanceDalImpl].in[Singleton]
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

    // --- bind other classes ---------------------TimePro
    bind[TimeProvider].to[SystemTimeProvider]
    bind[EthereumCallRequestBuilder]
    bind[EthereumBatchCallRequestBuilder]

    bind[TokenValueEstimator]
    bind[DustOrderEvaluator]
    bind[RingIncomeEstimator].to[RingIncomeEstimatorImpl]

    // --- bind primative types ---------------------

    bind[Timeout].toInstance(Timeout(2.second))

    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(config.getDouble("relay.dust-order-threshold"))

  }

  private def bindDBForNames(
    instance: DatabaseConfig[JdbcProfile],
    names: Seq[String]) = {
    names.foreach { name =>
      bind[DatabaseConfig[JdbcProfile]]
        .annotatedWithName(name)
        .toInstance(instance)
    }
  }
}
