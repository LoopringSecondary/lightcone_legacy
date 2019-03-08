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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.google.inject.Inject
import com.google.inject.name.Named
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.ethereum._
import io.lightcone.ethereum.extractor._
import io.lightcone.lib.TimeProvider
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.actors._
import io.lightcone.relayer.splitmerge._
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.data.BlockWithTxObject
import io.lightcone.relayer.ethereum._
import io.lightcone.relayer.external._
import io.lightcone.relayer.integration.mock._
import io.lightcone.relayer.socketio._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class CoreDeployerForTest @Inject()(
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
    eventExtractor: EventExtractor[BlockWithTxObject, AnyRef],
    socketIONotifier: SocketIONotifier,
    splitMergerProvider: SplitMergerProvider,
    externalTickerFetcher: ExternalTickerFetcher,
    fiatExchangeRateFetcher: FiatExchangeRateFetcher,
    system: ActorSystem)
    extends CoreDeployer {

  implicit var queryDataProvider: EthereumQueryDataProvider = _
  implicit var accessDataProvider: EthereumAccessDataProvider = _

  override def deployEthereum(): Lookup[ActorRef] = {
    actors
      .add(
        EthereumAccessActor.name, //
        system.actorOf(Props(new MockEthereumAccessActor()))
      )
      .add(
        EthereumQueryActor.name, //
        system.actorOf(Props(new MockEthereumQueryActor()))
      )
  }

  def deploy(
      ethAccessDataProvider: EthereumAccessDataProvider,
      ethQueryDataProvider: EthereumQueryDataProvider
    ) = {
    queryDataProvider = ethQueryDataProvider
    accessDataProvider = ethAccessDataProvider
    super.deploy()
  }

}
