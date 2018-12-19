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
import akka.actor._
import akka.cluster.sharding._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.actors.base.safefuture._
import scala.concurrent._
import org.loopring.lightcone.proto._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object MultiAccountManagerActor extends ShardedByAddress {
  val name = "multi_account_manager"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
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
    case req: XSubmitOrderReq =>
      throw ErrorException(
        ERR_UNEXPECTED_ACTOR_MSG,
        "MultiAccountManagerActor does not handle XSubmitOrderReq, use XSubmitSimpleOrderReq"
      )

    case XRecover.RecoverOrderReq(Some(raworder)) => raworder.owner
    case req: XCancelOrderReq ⇒ req.owner
    case req: XSubmitSimpleOrderReq ⇒ req.owner
    case req: XGetBalanceAndAllowancesReq ⇒ req.address
    case req: XAddressBalanceUpdated ⇒ req.address
    case req: XAddressAllowanceUpdated ⇒ req.address
  }
}

class MultiAccountManagerActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator)
    extends ActorWithPathBasedConfig(MultiAccountManagerActor.name)
    with ActorLogging {

  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val accountManagerActors = new MapBasedLookup[ActorRef]()
  var autoSwitchBackToReceive: Option[Cancellable] = None
  val extractAddress = MultiAccountManagerActor.extractAddress.lift

  //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      case e: Exception ⇒
        log.error(e.getMessage)
        Restart
    }

  override def preStart(): Unit = {
    super.preStart()

    autoSwitchBackToReceive = Some(
      context.system.scheduler
        .scheduleOnce(
          maxRecoverDurationMinutes.minute,
          self,
          XRecover.Finished(true)
        )
    )

    if (skiprecover) {
      log.warning(s"actor recover skipped: ${self.path}")
    } else {
      context.become(recover)
      log.debug(s"actor recover started: ${self.path}")
      actors.get(OrderRecoverCoordinator.name) !
        XRecover.Request(addressShardingEntity = entityName)
    }
  }

  def recover: Receive = {

    case req: XRecover.RecoverOrderReq => handleRequest(req)

    case XRecover.Finished(timeout) =>
      s"multi-account manager ${entityName} recover completed (timeout=${timeout})"
      context.become(receive)

    case msg: Any =>
      log.warning(s"message not handled during recover")
      sender ! XError(
        ERR_REJECTED_DURING_RECOVER,
        s"account manager ${entityName} is being recovered"
      )
  }

  def receive: Receive = {
    case req: Any => handleRequest(req)
  }

  private def handleRequest(req: Any) = extractAddress(req) match {
    case Some(address) => accountManagerActorFor(address) forward req
    case None =>
      throw ErrorException(
        ERR_UNEXPECTED_ACTOR_MSG,
        s"$req cannot be handled by ${getClass.getName}"
      )
  }

  protected def accountManagerActorFor(address: String): ActorRef = {
    if (!accountManagerActors.contains(address)) {
      log.info(s"created new account manager for address $address")
      accountManagerActors.add(
        address,
        context.actorOf(Props(new AccountManagerActor(address)), address)
      )
    }
    accountManagerActors.get(address)
  }

}
