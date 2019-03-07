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

package io.lightcone.relayer.actors

import akka.actor._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.extractor.{EventExtractor, TransactionData}
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum._
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

object PendingTxEventExtractorActor extends DeployedAsSingleton {

  val name = "pending_transaction_listener"

  def start(
      implicit
      config: Config,
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      eventDispatcher: EventDispatcher,
      eventExtractor: EventExtractor[TransactionData, AnyRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new PendingTxEventExtractorActor()))
  }

}

class PendingTxEventExtractorActor @Inject()(
    implicit
    val config: Config,
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val eventDispatcher: EventDispatcher,
    val eventExtractor: EventExtractor[TransactionData, AnyRef],
    val actors: Lookup[ActorRef])
    extends InitializationRetryActor {

  val subscribers = HttpConnector
    .connectorNames(config)
    .filter(node => node._2.wsPort > 0)
    .map(node => new PendingTransactionSubscriber(node._1, node._2))

  override def initialize(): Future[Unit] = Future {
    subscribers.foreach(_.start())
    becomeReady()
  }

  def ready: Receive = {
    case tx: Transaction =>
      eventExtractor
        .extractEvents(TransactionData(tx))
        .foreach(_.foreach(eventDispatcher.dispatch))
  }

}
