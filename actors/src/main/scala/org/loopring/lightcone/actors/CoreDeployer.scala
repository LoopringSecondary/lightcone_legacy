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
import com.google.inject.name.Named
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.entrypoint._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.jsonrpc.JsonRpcServer
import org.loopring.lightcone.actors.utils._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.slf4s.Logging
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

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
    supportedMarkets: SupportedMarkets,
    timeProvider: TimeProvider,
    timeout: Timeout,
    tokenManager: TokenManager,
    tve: TokenValueEvaluator,
    dispatchers: Seq[EventDispatcher[_, _]],
    system: ActorSystem)
    extends Object
    with Logging {

  def deploy() {

    //-----------deploy local actors-----------
    actors.add(BadMessageListener.name, BadMessageListener.start)
    actors.add(TokenMetadataRefresher.name, TokenMetadataRefresher.start)

    actors.add(
      MultiAccountManagerMessageValidator.name,
      MessageValidationActor(
        new MultiAccountManagerMessageValidator(),
        MultiAccountManagerActor.name,
        MultiAccountManagerMessageValidator.name
      )
    )

    actors.add(
      DatabaseQueryMessageValidator.name,
      MessageValidationActor(
        new DatabaseQueryMessageValidator(),
        DatabaseQueryActor.name,
        DatabaseQueryMessageValidator.name
      )
    )

    actors.add(
      EthereumQueryMessageValidator.name,
      MessageValidationActor(
        new EthereumQueryMessageValidator(),
        EthereumQueryActor.name,
        EthereumQueryMessageValidator.name
      )
    )

    actors.add(
      OrderbookManagerMessageValidator.name,
      MessageValidationActor(
        new OrderbookManagerMessageValidator(),
        OrderbookManagerActor.name,
        OrderbookManagerMessageValidator.name
      )
    )

    actors.add(
      TransactionRecordMessageValidator.name,
      MessageValidationActor(
        new TransactionRecordMessageValidator(),
        TransactionRecordActor.name,
        TransactionRecordMessageValidator.name
      )
    )

    Cluster(system).registerOnMemberUp {
      //-----------deploy sharded actors-----------
      actors.add(EthereumQueryActor.name, EthereumQueryActor.start)
      actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)
      actors.add(GasPriceActor.name, GasPriceActor.start)
      actors.add(OrderPersistenceActor.name, OrderPersistenceActor.start)
      actors.add(OrderRecoverActor.name, OrderRecoverActor.start)
      actors.add(MultiAccountManagerActor.name, MultiAccountManagerActor.start)
      actors.add(MarketManagerActor.name, MarketManagerActor.start)
      actors.add(OrderbookManagerActor.name, OrderbookManagerActor.start)

      actors.add(TransactionRecordActor.name, TransactionRecordActor.start)

      //-----------deploy singleton actors-----------
      // TODO(hongyu): 不能是Nil，需要完善，但是还没考虑好，EtherHttpConnector需要提前完成初始化，
      // 但是又不需要在启动每个实例时都初始化
      actors.add(EthereumClientMonitor.name, EthereumClientMonitor.start(Nil))
      actors.add(EthereumAccessActor.name, EthereumAccessActor.start)
      actors.add(OrderCutoffHandlerActor.name, OrderCutoffHandlerActor.start)
      actors.add(OrderRecoverCoordinator.name, OrderRecoverCoordinator.start)
      actors.add(OrderStatusMonitorActor.name, OrderStatusMonitorActor.start)

      actors.add(
        EthereumEventExtractorActor.name,
        EthereumEventExtractorActor.start
      )
      actors.add(
        EthereumEventImplementActor.name,
        EthereumEventImplementActor.start
      )
      actors.add(
        RingSettlementManagerActor.name,
        RingSettlementManagerActor.start
      )

      //-----------deploy local actors that depend on cluster aware actors-----------
      actors.add(EntryPointActor.name, EntryPointActor.start)

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
