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
import akka.event.LoggingReceive
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.persistence.TxEvents
import io.lightcone.relayer.base._
import io.lightcone.lib._
import io.lightcone.persistence._
import scala.concurrent._

// Owner: Yongfeng
object RingAndFillPersistenceActor extends DeployedAsSingleton {
  val name = "ring_and_fill_persistence"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new RingAndFillPersistenceActor()))
  }
}

class RingAndFillPersistenceActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends InitializationRetryActor {

  def ready: Receive = LoggingReceive {
    case req: TxEvents => {
      val fills = req.getFills.events
      if (fills.nonEmpty) dbModule.fillDal.saveFills(fills)
      else Future.successful(Unit)
    }

    case req: BlockEvent =>
      (for {
        result <- dbModule.fillDal.cleanUpForBlockReorganization(req)
      } yield result).sendTo(sender)
  }

}
