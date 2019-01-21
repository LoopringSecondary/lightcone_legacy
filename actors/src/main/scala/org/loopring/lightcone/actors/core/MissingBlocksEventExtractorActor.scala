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
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._

import scala.concurrent.{ExecutionContext, Future}

object MissingBlocksEventExtractorActor {
  val name = "missing_blocks_event_extractor"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      dispatchers: Seq[EventDispatcher[_]],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new MissingBlocksEventExtractorActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      name = MissingBlocksEventExtractorActor.name
    )

    system.actorOf(
      ClusterSingletonProxy
        .props(
          singletonManagerPath =
            s"/user/${MissingBlocksEventExtractorActor.name}",
          settings = ClusterSingletonProxySettings(system)
        ),
      name = s"${MissingBlocksEventExtractorActor.name}_proxy"
    )
  }

}

class MissingBlocksEventExtractorActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val eventDispatchers: Seq[EventDispatcher[_]],
    val dbModule: DatabaseModule)
    extends InitializationRetryActor
    with EventExtraction {
  val NEXT_RANGE = Notify("next_range")

  var untilBlock: Long = 0L //初始化为0，开始不需要获取区块

  override def initialize() = Future.successful {
    becomeReady()
    self ! NEXT_RANGE
  }

  def ready: Receive = handleMessage orElse {
    case NEXT_RANGE =>
    //TODO（yadong）等待永丰的接口来查询最新的Missing blocks
  }

  def process =
    processEvents.map(
      _ =>
        // TODO (yadong) 等待永丰的接口标记已经处理的高度
        if (blockData.height >= untilBlock) {
          self ! NEXT_RANGE
        }
    )

}
