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
import akka.pattern._
import akka.cluster._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject._
import com.google.inject.name.Named
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum._
import io.lightcone.ethereum._
import io.lightcone.relayer.jsonrpc.JsonRpcServer
import io.lightcone.relayer.validator._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data.Notify
import io.lightcone.relayer.external._
import io.lightcone.relayer.ethereum.event._
import org.slf4s.Logging
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent._

class CoreDeployer @Inject()(
    implicit
    @Named("deploy-actors-ignoring-roles") deployActorsIgnoringRoles: Boolean,
    actors: Lookup[ActorRef],
    actorMaterializer: ActorMaterializer,
    brb: EthereumBatchCallRequestBuilder,
    cluster: Cluster,
    config: Config,
    dcm: DatabaseConfigManager,
    dbModule: DatabaseModule,
    dustOrderEvaluator: DustOrderEvaluator,
    ec: ExecutionContext,
    ece: ExecutionContextExecutor,
    rb: EthereumCallRequestBuilder,
    rie: RingIncomeEvaluator,
    orderValidator: RawOrderValidator,
    ringBatchGenerator: RingBatchGenerator,
    metadataManager: MetadataManager,
    timeProvider: TimeProvider,
    timeout: Timeout,
    tve: TokenValueEvaluator,
    eventDispatcher: EventDispatcher,
    eventExtractor: EventExtractor,
    system: ActorSystem,
    tickerManager: TickerManager,
    currencyManager: CurrencyManager)
    extends Object
    with Logging {

  def deploy(): Unit = {

    //-----------deploy local actors-----------
    actors
      .add(
        BadMessageListener.name, //
        BadMessageListener.start
      )
      .add(
        MultiAccountManagerMessageValidator.name,
        MessageValidationActor(
          new MultiAccountManagerMessageValidator(),
          MultiAccountManagerActor.name,
          MultiAccountManagerMessageValidator.name
        )
      )
      .add(
        DatabaseQueryMessageValidator.name,
        MessageValidationActor(
          new DatabaseQueryMessageValidator(),
          DatabaseQueryActor.name,
          DatabaseQueryMessageValidator.name
        )
      )
      .add(
        EthereumQueryMessageValidator.name,
        MessageValidationActor(
          new EthereumQueryMessageValidator(),
          EthereumQueryActor.name,
          EthereumQueryMessageValidator.name
        )
      )
      .add(
        OrderbookManagerMessageValidator.name,
        MessageValidationActor(
          new OrderbookManagerMessageValidator(),
          OrderbookManagerActor.name,
          OrderbookManagerMessageValidator.name
        )
      )
      .add(
        TransactionRecordMessageValidator.name,
        MessageValidationActor(
          new TransactionRecordMessageValidator(),
          TransactionRecordActor.name,
          TransactionRecordMessageValidator.name
        )
      )
      .add(
        MetadataManagerValidator.name,
        MessageValidationActor(
          new MetadataManagerValidator(),
          MetadataManagerActor.name,
          MetadataManagerValidator.name
        )
      )

    //-----------deploy local actors-----------
    // TODO: OnMemberUp执行有时间限制，超时会有TimeoutException
    Cluster(system).registerOnMemberUp {
      //deploy ethereum conntionPools
      HttpConnector.start.foreach {
        case (name, actor) => actors.add(name, actor)
      }

      // TODO:需要再次确认启动依赖顺序问题
      var inited = false
      while (!inited) {
        try {
          val f =
            Future.sequence(HttpConnector.connectorNames(config).map {
              case (nodeName, node) =>
                val f1 = actors.get(nodeName) ? Notify("init")
                val r = Await.result(f1, timeout.duration)
                println(s"#### init HttpConnector ${r}")
                Future.unit
            })
          Await.result(f, timeout.duration)
          Thread.sleep(500)
          inited = true
        } catch {
          case e: Exception =>
            println(s"#### init HttpConnector ${e.printStackTrace}")
        }
      }

      // TODO：按照模块分布，因为启动有依赖顺序

      //-----------deploy singleton actors-----------
      actors
        .add(
          EthereumClientMonitor.name, //
          EthereumClientMonitor.start
        )
        .add(
          EthereumAccessActor.name, //
          EthereumAccessActor.start
        )
        .add(
          OrderRecoverCoordinator.name, //
          OrderRecoverCoordinator.start
        )
        .add(
          OrderStatusMonitorActor.name, //
          OrderStatusMonitorActor.start
        )
        .add(
          MetadataManagerActor.name, //
          MetadataManagerActor.start
        )
        .add(
          EthereumEventExtractorActor.name,
          EthereumEventExtractorActor.start
        )
        .add(
          MissingBlocksEventExtractorActor.name,
          MissingBlocksEventExtractorActor.start
        )
        .add(
          RingSettlementManagerActor.name, //
          RingSettlementManagerActor.start
        )
        .add(
          CMCCrawlerActor.name,
          CMCCrawlerActor.start
        )

      //-----------deploy sharded actors-----------
      actors
        .add(
          EthereumQueryActor.name, //
          EthereumQueryActor.start
        )
        .add(
          DatabaseQueryActor.name, //
          DatabaseQueryActor.start
        )
        .add(
          RingAndTradePersistenceActor.name,
          RingAndTradePersistenceActor.start
        )
        .add(
          GasPriceActor.name, //
          GasPriceActor.start
        )
        .add(
          OrderPersistenceActor.name, //
          OrderPersistenceActor.start
        )
        .add(
          OrderRecoverActor.name, //
          OrderRecoverActor.start
        )
        .add(
          MultiAccountManagerActor.name, //
          MultiAccountManagerActor.start
        )
        .add(
          MarketManagerActor.name, //
          MarketManagerActor.start
        )
        .add(
          OrderbookManagerActor.name, //
          OrderbookManagerActor.start
        )
        .add(
          OHLCDataHandlerActor.name, //
          OHLCDataHandlerActor.start
        )
        .add(
          TransactionRecordActor.name, //
          TransactionRecordActor.start
        )

      //-----------deploy local actors that depend on cluster aware actors-----------
      actors
        .add(
          EntryPointActor.name, //
          EntryPointActor.start
        )
        .add(
          MetadataRefresher.name, //
          MetadataRefresher.start
        )
        .add(
          KeepAliveActor.name, //
          KeepAliveActor.start
        )
        .add(
          ExternalDataRefresher.name,
          ExternalDataRefresher.start
        )

      //-----------deploy JSONRPC service-----------
      if (deployActorsIgnoringRoles ||
          cluster.selfRoles.contains("jsonrpc")) {
        val server = new JsonRpcServer(config, actors.get(EntryPointActor.name))
        with RpcBinding
        server.start
      }
    }
  }
}
