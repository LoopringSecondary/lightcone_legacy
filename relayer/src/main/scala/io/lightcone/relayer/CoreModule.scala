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
import io.lightcone.relayer.ethereum._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.persistence.dals._
import io.lightcone.persistence._
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data.SocketIOSubscription
import io.lightcone.relayer.ethereum.event._
import io.lightcone.relayer.socketio._
import io.lightcone.relayer.socketio.notifiers._

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
    bind[FillDal].to[FillDalImpl].asEagerSingleton
    bind[RingDal].to[RingDalImpl].asEagerSingleton
    bind[BlockDal].to[BlockDalImpl].asEagerSingleton
    bind[SettlementTxDal].to[SettlementTxDalImpl].asEagerSingleton
    bind[MarketMetadataDal].to[MarketMetadataDalImpl].asEagerSingleton
    bind[TokenMetadataDal].to[TokenMetadataDalImpl].asEagerSingleton
    bind[MissingBlocksRecordDal].to[MissingBlocksRecordDalImpl].asEagerSingleton
    bind[OHLCDataDal].to[OHLCDataDalImpl].asEagerSingleton

    // --- bind db services ---------------------
    bind[OrderService].to[OrderServiceImpl].asEagerSingleton
    bind[FillService].to[FillServiceImpl].asEagerSingleton
    bind[RingService].to[RingServiceImpl].asEagerSingleton
    bind[SettlementTxService].to[SettlementTxServiceImpl].asEagerSingleton
    bind[BlockService].to[BlockServiceImpl].asEagerSingleton
    bind[OHLCDataService].to[OHLCDataServiceImpl].asEagerSingleton

    // --- bind local singletons ---------------------
    bind[DatabaseModule].asEagerSingleton
    bind[MetadataManager].toInstance(new MetadataManagerImpl(0.6, 0.06))
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

    //bind socket listener
    bind[SocketIONotifier[SocketIOSubscription.ParamsForAccounts]]
      .to[AccountNotifier]
      .asEagerSingleton
    bind[SocketIONotifier[SocketIOSubscription.ParamsForActivities]]
      .to[ActivityNotifier]
      .asEagerSingleton
    bind[SocketIONotifier[SocketIOSubscription.ParamsForOrders]]
      .to[OrderNotifier]
      .asEagerSingleton
    bind[SocketIONotifier[SocketIOSubscription.ParamsForTickers]]
      .to[TickerNotifier]
      .asEagerSingleton
    bind[SocketIONotifier[SocketIOSubscription.ParamsForFills]]
      .to[FillNotifier]
      .asEagerSingleton
    bind[SocketIONotifier[SocketIOSubscription.ParamsForOrderbook]]
      .to[OrderBookNotifier]
      .asEagerSingleton

    // --- bind primative types ---------------------
    bind[Timeout].toInstance(Timeout(2.second))

    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(config.getDouble("relay.dust-order-threshold"))

    bind[Boolean]
      .annotatedWithName("deploy-actors-ignoring-roles")
      .toInstance(deployActorsIgnoringRoles)
  }

  // --- bind event dispatchers ---------------------
  @Provides
  def bindEventDispatcher(
      implicit
      actors: Lookup[ActorRef]
    ): EventDispatcher = {
    new EventDispatcherImpl(actors)
      .register(
        classOf[RingMinedEvent],
        MarketManagerActor.name,
        RingAndFillPersistenceActor.name
      )
      .register(
        classOf[CutoffEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[OrderFilledEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[OrdersCancelledOnChainEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[TokenBurnRateChangedEvent], //
        MetadataManagerActor.name
      )
      .register(
        classOf[TransferEvent], //
        TransactionRecordActor.name
      )
      .register(
        classOf[OHLCRawDataEvent], //
        OHLCDataHandlerActor.name
      )
      .register(
        classOf[BlockGasPricesExtractedEvent], //
        GasPriceActor.name
      )
      .register(
        classOf[AddressAllowanceUpdatedEvent],
        MultiAccountManagerActor.name
      )
      .register(
        classOf[AddressBalanceUpdatedEvent],
        MultiAccountManagerActor.name,
        RingSettlementManagerActor.name
      )

  }

  // --- bind event extractors ---------------------
  @Provides
  def bindEventExtractor(
      implicit
      config: Config,
      brb: EthereumBatchCallRequestBuilder,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      ec: ExecutionContext,
      metadataManager: MetadataManager,
      rawOrderValidatorArg: RawOrderValidator
    ): EventExtractor =
    new EventExtractorCompose(
      new BalanceAndAllowanceChangedExtractor(),
      new BlockGasPriceExtractor(),
      new CutoffEventExtractor(),
      new OnchainOrderExtractor(),
      new OrdersCancelledEventExtractor(),
      new RingMinedEventExtractor(),
      new TokenBurnRateEventExtractor()
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
