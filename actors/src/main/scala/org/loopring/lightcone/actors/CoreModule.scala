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
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

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

    bind[OrderDal].to[OrderDalImpl].in[Singleton]

    // val dbConfig: DatabaseConfig[JdbcProfile] =
    //   DatabaseConfig.forConfig("db.default", config)

    // val orderService: OrderService = new OrderServiceImpl()
    // val tradeService: TradeService = new TradeServiceImpl()

    // val tokenMetadataService = new TokenMetadataServiceImpl()
    // val settlementTxService: SettlementTxService = new SettlementTxServiceImpl()
    // val blockService: BlockService = new BlockServiceImpl()

    // val orderStatusMonitorService: OrderStatusMonitorService =
    //   new OrderStatusMonitorServiceImpl()

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

  }
}
