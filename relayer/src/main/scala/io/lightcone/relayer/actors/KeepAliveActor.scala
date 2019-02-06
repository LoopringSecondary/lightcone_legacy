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
import akka.cluster.singleton._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import javax.inject.Inject
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.proto._
import io.lightcone.core._
import scala.concurrent._

//目标：需要恢复的以及初始化花费时间较长的
//定时keepalive, 定时给需要监控的发送req，确认各个shard等需要初始化的运行正常，否则会触发他们的启动恢复
object KeepAliveActor extends DeployedAsSingleton {
  val name = "alive_keeper"
  val NOTIFY_MSG = "heartbeat"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      metadataManager: MetadataManager,
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new KeepAliveActor()))
  }
}

class KeepAliveActor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val metadataManager: MetadataManager)
    extends InitializationRetryActor
    with RepeatedJobActor {

  def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  def marketManagerActor = actors.get(MarketManagerActor.name)
  def multiAccountManagerActor = actors.get(MultiAccountManagerActor.name)

  val repeatedJobs = Seq(
    Job(
      name = "keep-alive",
      dalayInSeconds = 60, // 10 minutes
      initialDalayInSeconds = 10,
      run = () =>
        Future.sequence(
          Seq(
            initEtherHttpConnector(),
            initOrderbookManager(),
            initMarketManager(),
            initAccountManager()
          )
        )
    )
  )

  //定时发送请求，来各个需要初始化的actor保持可用
  def ready: Receive = receiveRepeatdJobs

  // TODO: market的配置读取，可以等待永丰处理完毕再优化
  private def initOrderbookManager(): Future[Unit] =
    for {
      _ <- Future.sequence(metadataManager.getValidMarketPairs map {
        case (_, marketPair) =>
          orderbookManagerActor ? Notify(
            KeepAliveActor.NOTIFY_MSG,
            marketPair.baseToken + "-" + marketPair.quoteToken
          )
      })
    } yield Unit

  private def initMarketManager(): Future[Unit] =
    for {
      _ <- Future.sequence(metadataManager.getValidMarketPairs map {
        case (_, marketPair) =>
          marketManagerActor ? Notify(
            KeepAliveActor.NOTIFY_MSG,
            marketPair.baseToken + "-" + marketPair.quoteToken
          )
      })
    } yield Unit

  private def initAccountManager(): Future[Unit] = {
    val numsOfShards = config.getInt("multi_account_manager.num-of-entities")
    for {
      _ <- Future.sequence((0 until numsOfShards) map { i =>
        multiAccountManagerActor ? Notify(KeepAliveActor.NOTIFY_MSG, i.toString)
      })
    } yield Unit
  }

  private def initEtherHttpConnector(): Future[Unit] =
    for {
      _ <- Future.sequence(HttpConnector.connectorNames(config).map {
        case (nodeName, node) =>
          actors.get(nodeName) ? Notify(KeepAliveActor.NOTIFY_MSG)
      })
    } yield Unit

}
