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

package org.loopring.lightcone.actors.utils

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Matchable
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto.OrderStatus._
import org.loopring.lightcone.proto._
import scala.concurrent._

object TokenMetadataRefresher {
  val name = "token_metadata_refresher"

  def start(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      tokenManager: TokenManager
    ) = {
    system.actorOf(
      Props(new TokenMetadataRefresher()),
      TokenMetadataRefresher.name
    )
  }
}

// main owner: 杜永丰
class TokenMetadataRefresher(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule,
    val tokenManager: TokenManager)
    extends Actor
    with ActorLogging
    with RepeatedJobActor {

  private val tokenMetadataService = dbModule.tokenMetadataService

  val repeatedJobs = Seq(
    Job(
      name = "sync-token-metadata",
      dalayInSeconds = 10 * 60, // 10 minutes
      run = () =>
        tokenMetadataService.getTokens(true).map { tokens =>
          tokenManager.reset(tokens)
        }
    )
  )

  override def receive: Receive = super.receive orElse LoggingReceive {
    case req: TokenBurnRateChangedEvent =>
      log.info(s"received TokenBurnRateChangedEvent ${req}")
      tokenMetadataService.updateBurnRate(req.token, req.burnRate)
      if (tokenManager.hasToken(req.token)) {
        tokenManager.addToken(
          tokenManager.getToken(req.token).meta.copy(burnRate = req.burnRate)
        )
      }
  }
}
