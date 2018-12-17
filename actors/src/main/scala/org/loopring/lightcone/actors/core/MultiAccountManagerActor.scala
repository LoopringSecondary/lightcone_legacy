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
import akka.actor.SupervisorStrategy.Restart
import akka.actor.{
  ActorLogging,
  ActorRef,
  ActorSystem,
  AllForOneStrategy,
  Props
}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MultiAccountManagerActor extends ShardedByAddress {
  val name = "multi_account_manager"

  def startShardRegion(
    )(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dustEvaluator: DustOrderEvaluator
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MultiAccountManagerActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }

  // 如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  val extractAddress: PartialFunction[Any, String] = {
    case req: XCancelOrderReq ⇒
      println(s"XCancelOrderReq ${req}")
      req.address
    case req: XSubmitOrderReq ⇒ req.address
    case req: XGetBalanceAndAllowancesReq ⇒ req.address
    case req: XAddressBalanceUpdated ⇒ req.address
    case req: XAddressAllowanceUpdated ⇒ req.address
  }
}

class MultiAccountManagerActor(
  )(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends ActorWithPathBasedConfig(MultiAccountManagerActor.name)
    with OrderRecoverSupport
    with ActorLogging {

  log.info(s"MultiAccountManagerActor entity name ${entityName}")
  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
      case _: Exception ⇒ Restart
    }

  val accountManagerActors = new MapBasedLookup[ActorRef]()

  requestOrderRecovery(
    XOrderRecoverySettings(
      selfConfig.getBoolean("skip-recovery"),
      selfConfig.getInt("recover-batch-size"),
      entityName,
      None
    )
  )

  def receive: Receive = {
    case req: Any =>
      val addressOpt = MultiAccountManagerActor.extractAddress.lift(req)
      addressOpt match {
        case Some(address) => accountManagerActorFor(address) forward req
        case None =>
          throw ErrorException(ERR_INVALID_REQ, "req cannot be handlled")
      }
  }

  def accountManagerActorFor(address: String): ActorRef = {
    val actorName = address
    if (!accountManagerActors.contains(actorName)) {
      val newAccountActor =
        context.actorOf(Props(new AccountManagerActor(address)), actorName)
      accountManagerActors.add(actorName, newAccountActor)
    }
    accountManagerActors.get(actorName)
  }

  protected def recoverOrder(xraworder: XRawOrder) = {
    accountManagerActorFor(xraworder.owner) ? XSubmitOrderReq(
      xraworder.owner,
      Some(xraworder)
    )
  }

}
