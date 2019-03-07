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
import io.lightcone.ethereum.extractor._
import io.lightcone.ethereum.extractor.block.{
  AllowanceUpdateAddressExtractor,
  BalanceUpdateAddressExtractor
}
import io.lightcone.ethereum.extractor.tx.{
  TxApprovalEventExtractor,
  TxTransferEventExtractor
}
import io.lightcone.ethereum.persistence._
import io.lightcone.relayer.data._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.external._
import io.lightcone.relayer.splitmerge._
import io.lightcone.relayer.socketio._
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
      "dbconfig-dal-token-info",
      "dbconfig-dal-order",
      "dbconfig-dal-ring",
      "dbconfig-dal-token-balance",
      "dbconfig-dal-block",
      "dbconfig-dal-settlement-tx",
      "dbconfig-dal-market-metadata",
      "dbconfig-dal-missing-blocks-record",
      "dbconfig-dal-ohlc-data",
      "dbconfig-dal-fill",
      "dbconfig-dal-token-ticker-record",
      "dbconfig-dal-cmc-ticker-config"
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
    bind[TokenTickerRecordDal].to[TokenTickerRecordDalImpl].asEagerSingleton
    bind[TokenInfoDal].to[TokenInfoDalImpl].asEagerSingleton
    bind[CMCCrawlerConfigForTokenDal]
      .to[CMCCrawlerConfigForTokenDalImpl]
      .asEagerSingleton

    // --- bind db services ---------------------
    bind[OrderService].to[OrderServiceImpl].asEagerSingleton
    bind[SettlementTxService].to[SettlementTxServiceImpl].asEagerSingleton
    bind[BlockService].to[BlockServiceImpl].asEagerSingleton
    bind[OHLCDataService].to[OHLCDataServiceImpl].asEagerSingleton

    bind[SocketIONotifier].to[RelayerNotifier].asEagerSingleton

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

    bind[SplitMergerProvider].to[DefaultSplitMergerProvider].asEagerSingleton
    bind[ExternalTickerFetcher].to[CMCExternalTickerFetcher].asEagerSingleton
    bind[FiatExchangeRateFetcher]
      .to[SinaFiatExchangeRateFetcher]
      .asEagerSingleton

//    bind[EventExtractor[BlockWithTxObject, AnyRef]]
//      .to[DefaultEventExtractor]
//      .asEagerSingleton

    // --- bind primative types ---------------------
    bind[Timeout].toInstance(Timeout(2.second))

    bind[Double]
      .annotatedWithName("dust-order-threshold")
      .toInstance(config.getDouble("relay.dust-order-threshold"))

    bind[Boolean]
      .annotatedWithName("deploy-actors-ignoring-roles")
      .toInstance(deployActorsIgnoringRoles)
  }

  // --- bind tx event extractors ---------------------
  @Provides
  def bindTxEventExtractor(
      implicit
      ec: ExecutionContext,
      metadataManager: MetadataManager,
      config: Config
    ): EventExtractor[TransactionData, AnyRef] = {
    EventExtractor.compose[TransactionData, AnyRef]( //
      new TxCutoffEventExtractor(),
      new TxRingMinedEventExtractor(),
      new TxTokenBurnRateEventExtractor(),
      new TxTransferEventExtractor(),
      new TxApprovalEventExtractor() // more tx event extractors
    )
  }

  @Provides
  def bindBlockEventExtractor(
      implicit
      ec: ExecutionContext,
      metadataManager: MetadataManager,
      config: Config,
      actors: Lookup[ActorRef],
      timeout: Timeout,
      txEventExtractor: EventExtractor[TransactionData, AnyRef]
    ): DefaultEventExtractor = {

    val ethereumAccess = () => actors.get(EthereumAccessActor.name)
    val approvalEventExtractor = new TxApprovalEventExtractor()
    val txTransferEventExtractor = new TxTransferEventExtractor()

    new DefaultEventExtractor(
      EventExtractor.compose[BlockWithTxObject, AnyRef](
        new BlockGasPriceExtractor(),
        new AllowanceUpdateAddressExtractor(
          ethereumAccess,
          approvalEventExtractor
        ),
        new BalanceUpdateAddressExtractor(
          ethereumAccess,
          txTransferEventExtractor
        )
      )
    )
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
      .register(classOf[CutoffEvent], MultiAccountManagerActor.name)
      .register(classOf[OrderFilledEvent], MultiAccountManagerActor.name)
      .register(
        classOf[OrdersCancelledOnChainEvent],
        MultiAccountManagerActor.name
      )
      .register(
        classOf[TokenBurnRateChangedEvent], //
        MetadataManagerActor.name
      )
      .register(
        classOf[OHLCRawData], //
        MarketHistoryActor.name
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
