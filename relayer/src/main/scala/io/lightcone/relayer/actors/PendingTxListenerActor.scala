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
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.relayer.ethereum.HttpConnector
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

object PendingTxListenerActor extends DeployedAsSingleton {

  val name = "pending_transaction_listener"

  def start(
      implicit
      config: Config,
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new PendingTxListenerActor()))
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

  val selfConfig = config.getConfig(PendingTxListenerActor.name)
  val checkInterval = selfConfig.getInt("check-interval-seconds")
  val nodeNames: Seq[String] = HttpConnector.connectorNames(config).keys.toSeq
  var filters: Map[String, String] = Map.empty

  override def initialize(): Future[Unit] = {
    if (nodeNames.forall(actors.contains)) {
      Future
        .sequence(nodeNames.map { nodeName =>
          (actors.get(nodeName) ? NewPendingTransactionFilter.Req())
            .mapAs[NewPendingTransactionFilter.Res]
            .map(res => nodeName -> res.result)
        })
        .map(res => filters = res.toMap)
    } else Future.failed(new Exception("Ethereum is not ready"))
  }

  val repeatedJobs: Seq[Job] = {
    Seq(
      Job(
        name = PendingTxListenerActor.name,
        dalayInSeconds = checkInterval,
        run = () => getPendingTxs
      )
    )
  }

  def getPendingTxs = {
    for {
      hashSeqs <- Future
        .sequence(filters.map { filter =>
          (actors.get(filter._1) ? GetFilterChanges
            .Req(filterId = filter._2))
            .mapAs[GetFilterChanges.Res]
            .map(res => filter._1 -> res)
        })
        .map(res => res.filter(_._2.result.nonEmpty))
        .map(resp => resp.map(node => node._1 -> node._2.result).toSeq)
      txs: Seq[Transaction] <- Future
        .sequence(hashSeqs.map { hashSeq =>
          val batchReq = BatchGetTransactions.Req(
            hashSeq._2.map(hash => GetTransactionByHash.Req(hash))
          )
          (actors.get(hashSeq._1) ? batchReq)
            .mapAs[BatchGetTransactions.Res]
            .map(_.resps.map(_.result))
        })
        .map(_.flatten)
        .map(_.filter(_.nonEmpty).distinct.map(_.get))
    } yield txs.foreach(self ! _)
  }

  def ready: Receive = super.receiveRepeatdJobs orElse {
    case tx: Transaction =>
    //TODO(yadong) 解析Transaction，把事件发送到对应的Actor

  }

}
