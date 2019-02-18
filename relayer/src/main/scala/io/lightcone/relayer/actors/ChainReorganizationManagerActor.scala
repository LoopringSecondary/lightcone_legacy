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
import akka.event.LoggingReceive

//目标：需要恢复的以及初始化花费时间较长的
//定时keepalive, 定时给需要监控的发送req，确认各个shard等需要初始化的运行正常，否则会触发他们的启动恢复
object ChainReorganizationManagerActor extends DeployedAsSingleton {
  val name = "chain_reorg_manager"

  def start(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    chainReorgHandler: ChainReorganizationManager,
    // dbModule: DatabaseModule,
    deployActorsIgnoringRoles: Boolean): ActorRef = {
    startSingleton(Props(new ChainReorganizationManagerActor()))
  }
}

class ChainReorganizationManagerActor @Inject() (
  implicit val config: Config,
  val ec: ExecutionContext,
  val timeProvider: TimeProvider,
  val timeout: Timeout,
  val chainReorgHandler: ChainReorganizationManager,
  val actors: Lookup[ActorRef])
  extends InitializationRetryActor
  with Stash
  with ActorLogging {

  import MarketMetadata.Status._

  // def orderbookManagerActor = actors.get(OrderbookManagerActor.name)
  // def marketManagerActor = actors.get(MarketManagerActor.name)
  // def multiAccountManagerActor = actors.get(MultiAccountManagerActor.name)

  def ready: Receive = LoggingReceive {
    case _ =>
  }
}
