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
import org.loopring.lightcone.actors.base._
import akka.cluster.singleton._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode.ERR_INTERNAL_UNKNOWN
import org.loopring.lightcone.proto._
import org.loopring.lightcone.ethereum.data.Address

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Random

object RingSettlementManagerActor {
  val name = "ring_settlement"

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

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    system.actorOf(
      ClusterSingletonManager.props(
        singletonProps = Props(new RingSettlementManagerActor()),
        terminationMessage = PoisonPill,
        settings = ClusterSingletonManagerSettings(system).withRole(roleOpt)
      ),
      RingSettlementManagerActor.name
    )

    system.actorOf(
      ClusterSingletonProxy.props(
        singletonManagerPath = s"/user/${RingSettlementManagerActor.name}",
        settings = ClusterSingletonProxySettings(system)
      ),
      name = s"${RingSettlementManagerActor.name}_proxy"
    )
  }
}

class RingSettlementManagerActor(
    implicit
    system: ActorSystem,
    val config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    dbModule: DatabaseModule)
    extends Actor {

  val selfConfig = config.getConfig(RingSettlementManagerActor.name)

  var ringSettlementActors: Map[String, ActorRef] = Map.empty

  var invalidRingSettlementActors = mutable.HashMap.empty[String, ActorRef]

  val miniMinerBalance = BigInt(selfConfig.getString("mini-miner-balance"))

  override def preStart() = {
    ringSettlementActors = selfConfig
      .getConfigList("miners")
      .asScala
      .map(minerConfig => {
        val miner = minerConfig.getString("transaction-origin")
        val item = Address(miner).toString ->
          context.actorOf(
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
        item
      })
      .toMap
  }

  override def receive: Receive = {
    case req: SettleRings =>
      if (ringSettlementActors.nonEmpty) {
        ringSettlementActors
          .toSeq(Random.nextInt(ringSettlementActors.size))
          ._2 forward req
      } else {
        sender ! ErrorException(
          ERR_INTERNAL_UNKNOWN,
          message = "no invalid miner to handle this XSettleRingsReq"
        )
      }

    case ba: AddressBalanceUpdated =>
      if (Address(ba.token).isZero) {
        val balance = BigInt(ba.balance.toByteArray)
        if (balance > miniMinerBalance && invalidRingSettlementActors.contains(
              ba.address
            )) {
          ringSettlementActors += (ba.address -> invalidRingSettlementActors(
            ba.address
          ))
          invalidRingSettlementActors = invalidRingSettlementActors - ba.address
        } else if (balance <= miniMinerBalance && ringSettlementActors.contains(
                     ba.address
                   )) {
          invalidRingSettlementActors += (ba.address -> ringSettlementActors(
            ba.address
          ))
          ringSettlementActors = ringSettlementActors - ba.address
        }
      }
    case msg =>
      println(s"ring settlement manager received $msg")
  }
}
