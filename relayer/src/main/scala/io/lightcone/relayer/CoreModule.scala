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

package io.lightcone.relayer

import akka.actor._
import akka.cluster._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject._
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum.Dispatchers._
import io.lightcone.relayer.ethereum.{EventDispatcher, _}
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.persistence.dals._
import io.lightcone.persistence._
import io.lightcone.relayer.ethereum.event._
import io.lightcone.ethereum._
import io.lightcone.relayer.data._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import org.slf4s.Logging

// Owner: Daniel
class CoreModule(
    config: Config,
    deployActorsIgnoringRoles: Boolean = false)
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
      "dbconfig-dal-ring",
      "dbconfig-dal-token-balance",
      "dbconfig-dal-block",
      "dbconfig-dal-settlement-tx",
      "dbconfig-dal-market-metadata",
      "dbconfig-dal-missing-blocks-record",
      "dbconfig-dal-ohlc-data"
    )

    // --- bind dals ---------------------
    bind[OrderDal].to[OrderDalImpl].asEagerSingleton
    bind[TradeDal].to[TradeDalImpl].asEagerSingleton
    bind[RingDal].to[RingDalImpl].asEagerSingleton
    bind[BlockDal].to[BlockDalImpl].asEagerSingleton
    bind[SettlementTxDal].to[SettlementTxDalImpl].asEagerSingleton
    bind[MarketMetadataDal].to[MarketMetadataDalImpl].asEagerSingleton
    bind[TokenMetadataDal].to[TokenMetadataDalImpl].asEagerSingleton
    bind[MissingBlocksRecordDal].to[MissingBlocksRecordDalImpl].asEagerSingleton
    bind[OHLCDataDal].to[OHLCDataDalImpl].asEagerSingleton

    // --- bind db services ---------------------
    bind[OrderService].to[OrderServiceImpl].asEagerSingleton
    bind[TradeService].to[TradeServiceImpl].asEagerSingleton
    bind[RingService].to[RingServiceImpl].asEagerSingleton
    bind[SettlementTxService].to[SettlementTxServiceImpl].asEagerSingleton
    bind[BlockService].to[BlockServiceImpl].asEagerSingleton
    bind[OHLCDataService].to[OHLCDataServiceImpl].asEagerSingleton

    // --- bind local singletons ---------------------
    bind[DatabaseModule].asEagerSingleton
    bind[MetadataManager].asEagerSingleton

    bind[Lookup[ActorRef]].toInstance(new MapBasedLookup[ActorRef]())

    // --- bind other classes ---------------------
    bind[TimeProvider].to[SystemTimeProvider]
    bind[EthereumCallRequestBuilder]
    bind[EthereumBatchCallRequestBuilder]

    bind[TokenValueEvaluator]
    bind[DustOrderEvaluator]
    bind[RingIncomeEvaluator].to[RingIncomeEvaluatorImpl]
    bind[RawOrderValidator].to[RawOrderValidatorImpl]
    bind[RingBatchGenerator].to[Protocol2RingBatchGenerator]

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
    bind[EventExtractor[OHLCRawData]].to[OHLCRawDataExtractor]
    bind[EventExtractor[BlockGasPrices]].to[BlockGasPriceExtractor]

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
    bind[EventDispatcher[OHLCRawData]].to[OHLCRawDataEventDispatcher]
    bind[EventDispatcher[BlockGasPrices]].to[BlockGasPricesDispatcher]

    // --- bind primative types ---------------------
    bind[Timeout].toInstance(Timeout(2.second))

    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(config.getDouble("relay.dust-order-threshold"))

    bind[Boolean]
      .annotatedWithName("deploy-actors-ignoring-roles")
      .toInstance(deployActorsIgnoringRoles)
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
      ohlcRawDataEventDispatcher: EventDispatcher[OHLCRawData],
      blockGasPricesDispatcher: EventDispatcher[BlockGasPrices],
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
      ohlcRawDataEventDispatcher,
      blockGasPricesDispatcher,
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
