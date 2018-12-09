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

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.util.Timeout
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.persistence._

import scala.concurrent.{ ExecutionContext, Future }

object TokenMetadataActor {
  val name = "token_metadata"
}

class TokenMetadataActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    tmm: TokenMetadataManager,
    dbModule: DatabaseModule
) extends RepeatedJobActor
  with ActorLogging {
  private val tokenMetadata = dbModule.tokenMetadata
  val syncJob = Job(
    id = 1,
    name = "syncTokenValue",
    scheduleDelay = 10000,
    run = () ⇒ tokenMetadata.getTokens(true)
  )
  initAndStartNextRound(syncJob)

  override def receive: Receive = super.receive orElse LoggingReceive {
    case _ ⇒
  }
}
