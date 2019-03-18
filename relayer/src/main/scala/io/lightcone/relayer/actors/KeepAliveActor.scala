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
import javax.inject.Inject
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence._
import io.lightcone.relayer.data._
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

//TODO(HONGYU):有了分片广播，激活时应该优先使用分片广播
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

  import MarketMetadata.Status._
  val numsOfAccountShards = config.getInt("multi_account_manager.num-of-shards")

  @inline @inline def orderbookManagerActor =
    actors.get(OrderbookManagerActor.name)
  @inline def marketManagerActor = actors.get(MarketManagerActor.name)

  @inline def multiAccountManagerActor =
    actors.get(MultiAccountManagerActor.name)

  val repeatedJobs = Seq(
    Job(
      name = "keep-alive",
      dalayInSeconds = 60, // 10 minutes
      initialDalayInSeconds = 10,
      run = () =>
        Future.sequence(
          Seq(
            pingEtherHttpConnector(),
            pingOrderbookManager(),
            pingMarketManager(),
            pingAccountManager()
          )
        )
    )
  )

  //定时发送请求，来各个需要初始化的actor保持可用
  def ready: Receive = receiveRepeatdJobs

  // TODO: market的配置读取，可以等待永丰处理完毕再优化
  private def pingOrderbookManager(): Future[Unit] =
    for {
      _ <- Future.sequence(metadataManager.getMarkets(ACTIVE, READONLY) map {
        case m =>
          val marketPair = m.metadata.get.marketPair.get
          orderbookManagerActor ? Notify(
            KeepAliveActor.NOTIFY_MSG,
            marketPair.baseToken + "-" + marketPair.quoteToken
          )
      })
    } yield Unit

  private def pingMarketManager(): Future[Unit] =
    for {
      _ <- Future.sequence(metadataManager.getMarkets(ACTIVE, READONLY) map {
        case m =>
          val marketPair = m.metadata.get.marketPair.get
          marketManagerActor ? Notify(
            KeepAliveActor.NOTIFY_MSG,
            marketPair.baseToken + "-" + marketPair.quoteToken
          )
      })
    } yield Unit

  private def pingAccountManager(): Future[Unit] = {
    for {
      _ <- Future.sequence((0 until numsOfAccountShards) map { i =>
        multiAccountManagerActor ? Notify(KeepAliveActor.NOTIFY_MSG, i.toString)
      })
    } yield Unit
  }

  private def pingEtherHttpConnector(): Future[Unit] =
    for {
      _ <- Future.sequence(HttpConnector.connectorNames(config).map {
        case (nodeName, node) =>
          if (actors.contains(nodeName)) {
            actors.get(nodeName) ? Notify(KeepAliveActor.NOTIFY_MSG)
          } else Future.unit
      })
    } yield Unit

}
