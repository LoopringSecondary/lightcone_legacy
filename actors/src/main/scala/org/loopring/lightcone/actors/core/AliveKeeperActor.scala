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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.singleton._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import javax.inject.Inject
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum.HttpConnector
import org.loopring.lightcone.actors.validator.SupportedMarkets
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._

import scala.concurrent._

//目标：需要恢复的以及初始化花费时间较长的
//定时keepalive, 定时给需要监控的发送req，确认各个shard等需要初始化的运行正常，否则会触发他们的启动恢复
object AliveKeeperActor {
  val name = "alive_keeper"
  val NOTIFY_MSG = "init"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      supportedMarkets: SupportedMarkets,
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new AliveKeeperActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      AliveKeeperActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${AliveKeeperActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${AliveKeeperActor.name}_proxy"
    )
  }
}

class AliveKeeperActor @Inject()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val supportedMarkets: SupportedMarkets)
    extends ActorWithPathBasedConfig(AliveKeeperActor.name)
    with RepeatedJobActor {

  val orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  val marketManagerActor = actors.get(MarketManagerActor.name)
  val multiAccountManagerActor = actors.get(MultiAccountManagerActor.name)

  val repeatedJobs = Seq(
    Job(
      name = "keep-alive",
      dalayInSeconds = 60, // 10 minutes
      run = () =>
        for {
          _ <- initEtherHttpConnector()
          _ <- initOrderbookManager()
          _ <- initMarketManager()
          _ <- initAccountManager()
        } yield Unit
    )
  )

  //定时发送请求，来各个需要初始化的actor保持可用
  def ready: Receive = receiveRepeatdJobs

  //todo: market的配置读取，可以等待永丰处理完毕再优化
  private def initOrderbookManager(): Future[Unit] =
    for {
      _ <- Future.sequence(supportedMarkets.markets map {
        case (_, marketId) =>
          orderbookManagerActor ? Notify(
            "init",
            marketId.primary + "-" + marketId.secondary
          )
      })
    } yield Unit

  private def initMarketManager(): Future[Unit] =
    for {
      _ <- Future.sequence(supportedMarkets.markets map {
        case (_, marketId) =>
          marketManagerActor ? Notify(
            "init",
            marketId.primary + "-" + marketId.secondary
          )
      })
    } yield Unit

  private def initAccountManager(): Future[Unit] = {
    val numsOfShards = config.getInt("multi_account_manager.num-of-shards")
    for {
      _ <- Future.sequence((0 until numsOfShards) map { i =>
        multiAccountManagerActor ? Notify("init", i.toString)
      })
    } yield Unit
  }

  private def initEtherHttpConnector(): Future[Unit] =
    for {
      _ <- Future.sequence(HttpConnector.connectorNames(config).map {
        case (nodeName, node) => actors.get(nodeName) ? Notify("init")
      })
    } yield Unit

}
