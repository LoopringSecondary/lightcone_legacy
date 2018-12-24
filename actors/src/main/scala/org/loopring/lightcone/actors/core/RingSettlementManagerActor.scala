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
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.{
  XGetNonceReq,
  XGetNonceRes,
  XSettleRingsReq
}
import akka.pattern._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.proto.XErrorCode.ERR_INTERNAL_UNKNOWN
import org.web3j.utils.Numeric

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Random
import scala.concurrent.{ExecutionContext, Future}

object RingSettlementManagerActor {
  val name = "ring_settlement"
}

class RingSettlementManagerActor(
  )(
    implicit system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends Actor {

  val ethereumAccessorActor = actors.get(EthereumAccessActor.name)

  val ringSettlementActors =
    config
      .getConfigList("ring_settlement.miners")
      .asScala
      .map(minerConfig ⇒ {
        minerConfig.getString("transaction_origin") → context.actorOf(
          Props(
            new RingSettlementActor()(
              config = minerConfig.withFallback(config),
              ec = ec,
              timeProvider = timeProvider,
              timeout = timeout,
              actors = actors,
              dbModule = dbModule
            )
          )
        )
      })
      .toMap

  override def receive: Receive = {
    case req: XSettleRingsReq ⇒
      if (ringSettlementActors.nonEmpty) {
        ringSettlementActors
          .toSeq(Random.nextInt(ringSettlementActors.size))
          ._2 forward req
      } else {
        sender ! ErrorException(
          ERR_INTERNAL_UNKNOWN,
          message = "no invalid miner to handle this req"
        )
      }

  }
}
