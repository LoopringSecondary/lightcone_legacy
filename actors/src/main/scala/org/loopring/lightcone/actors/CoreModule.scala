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
import org.loopring.lightcone.actors.ethereum.Dispatchers._
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
import org.loopring.lightcone.actors.ethereum.event._
import org.loopring.lightcone.proto._

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

  val dbConfigManager = new DatabaseConfigManager(config)

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
    bind[DatabaseConfigManager].toInstance(dbConfigManager)

    bindDatabaseConfigProviderForNames(
      "dbconfig-dal-token-metadata",
      "dbconfig-dal-order",
      "dbconfig-dal-trade",
      "dbconfig-dal-token-balance",
      "dbconfig-dal-block",
      "dbconfig-dal-settlement-tx",
      "dbconfig-dal-order-status-monitor",
      "dbconfig-dal-market-metadata"
    )

    // --- bind event extractors ---------------------
    bind[EventExtractor[AddressAllowanceUpdated]]
      .to[AllowanceChangedAddressExtractor]

    bind[EventExtractor[AddressBalanceUpdated]]
      .to[BalanceChangedAddressExtractor]

    bind[EventExtractor[OrdersCancelledEvent]]
      .to[OrdersCancelledEventExtractor]

    bind[EventExtractor[TokenBurnRateChangedEvent]]
      .to[TokenBurnRateEventExtractor]

    bind[EventExtractor[CutoffEvent]].to[CutoffEventExtractor]
    bind[EventExtractor[RawOrder]].to[OnchainOrderExtractor]
    bind[EventExtractor[RingMinedEvent]].to[RingMinedEventExtractor]
    bind[EventExtractor[TransferEvent]].to[TransferEventExtractor]
    bind[EventExtractor[OrderFilledEvent]].to[OrderFillEventExtractor]

    // --- bind event dispatchers ---------------------
    bind[EventDispatcher[AddressAllowanceUpdated]]
      .to[AllowanceEventDispatcher]

    bind[EventDispatcher[AddressBalanceUpdated]]
      .to[BalanceEventDispatcher]

    bind[EventDispatcher[OrdersCancelledEvent]]
      .to[OrdersCancelledEventDispatcher]

    bind[EventDispatcher[OrderFilledEvent]]
      .to[OrderFilledEventDispatcher]

    bind[EventDispatcher[TokenBurnRateChangedEvent]]
      .to[TokenBurnRateChangedEventDispatcher]

    bind[EventDispatcher[RingMinedEvent]].to[RingMinedEventDispatcher]
    bind[EventDispatcher[TransferEvent]].to[TransferEventDispatcher]
    bind[EventDispatcher[CutoffEvent]].to[CutoffEventDispatcher]

    // --- bind dals ---------------------
    bind[OrderDal].to[OrderDalImpl].asEagerSingleton
    bind[TradeDal].to[TradeDalImpl].asEagerSingleton
    bind[BlockDal].to[BlockDalImpl].asEagerSingleton
    bind[SettlementTxDal].to[SettlementTxDalImpl].asEagerSingleton
    bind[OrderStatusMonitorDal].to[OrderStatusMonitorDalImpl].asEagerSingleton
    bind[MarketMetadataDal].to[MarketMetadataDalImpl].asEagerSingleton
    bind[TokenMetadataDal].to[TokenMetadataDalImpl].asEagerSingleton

    // --- bind db services ---------------------
    bind[OrderService].to[OrderServiceImpl].asEagerSingleton
    bind[TradeService].to[TradeServiceImpl].asEagerSingleton
    bind[SettlementTxService].to[SettlementTxServiceImpl].asEagerSingleton
    bind[BlockService].to[BlockServiceImpl].asEagerSingleton

    bind[OrderStatusMonitorService]
      .to[OrderStatusMonitorServiceImpl]
      .asEagerSingleton

    // --- bind local singletons ---------------------
    bind[DatabaseModule].asEagerSingleton
    bind[TokenManager].asEagerSingleton

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

  @Provides
  def getEventDispathcers(
      balanceEventDispatcher: EventDispatcher[AddressBalanceUpdated],
      ringMinedEventDispatcher: EventDispatcher[RingMinedEvent],
      orderFilledEventDispatcher: EventDispatcher[OrderFilledEvent],
      cutoffEventDispatcher: EventDispatcher[CutoffEvent],
      transferEventDispatcher: EventDispatcher[TransferEvent],
      allowanceEventDispatcher: EventDispatcher[AddressAllowanceUpdated],
      ordersCancelledEventDispatcher: EventDispatcher[OrdersCancelledEvent],
      tokenBurnRateChangedEventDispatcher: EventDispatcher[
        TokenBurnRateChangedEvent
      ]
    ): Seq[EventDispatcher[_]] =
    Seq(
      balanceEventDispatcher,
      ringMinedEventDispatcher,
      orderFilledEventDispatcher,
      cutoffEventDispatcher,
      transferEventDispatcher,
      allowanceEventDispatcher,
      ordersCancelledEventDispatcher,
      tokenBurnRateChangedEventDispatcher
    )

  private def bindDatabaseConfigProviderForNames(names: String*) = {
    bind[DatabaseConfig[JdbcProfile]]
      .toProvider(new Provider[DatabaseConfig[JdbcProfile]] {
        def get() = dbConfigManager.getDatabaseConfig("db.default")
      })

    names.foreach { name =>
      bind[DatabaseConfig[JdbcProfile]]
        .annotatedWithName(name)
        .toProvider(new Provider[DatabaseConfig[JdbcProfile]] {
          def get() = dbConfigManager.getDatabaseConfig(s"db.${name}")
        })
    }
  }
}
