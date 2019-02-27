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

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.data.NewPendingTransactionFilter
import io.lightcone.relayer.ethereum.HttpConnector
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

object PendingTxListenerActor extends DeployedAsSingleton{

  val name = "pending_transaction_listener"

  def start(
             implicit
             system: ActorSystem,
             ec: ExecutionContext,
             timeout: Timeout,
             deployActorsIgnoringRoles: Boolean
           ): ActorRef = {
    startSingleton(Props(new PendingTxListenerActor()()))
  }

}


class PendingTxListenerActor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends InitializationRetryActor
    with RepeatedJobActor {

  val selfConfig = config.getConfig(E.name)
  val checkInterval =
  val nodeNames: Seq[String] = HttpConnector.connectorNames(config).keys.toSeq
  var filters: Map[String, String] = Map.empty

  override def initialize(): Future[Unit] = {
    if (nodes.forall(actors.contains)) {
      Future
        .sequence(nodeNames.map { nodeName =>
          (actors.get(nodeName) ? NewPendingTransactionFilter.Req())
            .mapAs[NewPendingTransactionFilter.Res]
            .map(res => nodeName -> res.result)
        })
        .map(res => filters = res.toMap)
    } else Future.failed(new Exception("Ethereum is not ready"))
  }

  override val repeatedJobs: Seq[Job] = {
      Seq(
        Job(
          name = pending_transaction_listener,

        )
      )


  }

  def getPendingTxs = {

    filters.




  }



  def ready: Receive = super.receiveRepeatdJobs



}
