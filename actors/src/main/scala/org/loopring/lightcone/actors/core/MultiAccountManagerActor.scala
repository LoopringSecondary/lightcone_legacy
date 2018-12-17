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
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._
import scala.concurrent._
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
    case req: XCancelOrderReq ⇒ req.address
    case req: XSubmitOrderReq ⇒ req.address
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

  val skipRecovery = selfConfig.getBoolean("skip-recovery")
  val accountManagerActors = new MapBasedLookup[ActorRef]()
  var autoSwitchBackToReceive: Option[Cancellable] = None

  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 5 second) {
      //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
      case e: Exception ⇒
        log.error(e.getMessage)
        Restart
    }

  override def preStart(): Unit = {
    super.preStart()

    if (skipRecovery) {
      log.warning(s"actor recover skipped: ${self.path}")
    } else {
      context.become(recover)
      log.debug(s"actor recover started: ${self.path}")
      actors.get(OrderRecoverCoordinator.name) !
        XRecoverReq(addressShardingEntities = Seq(entityName))
    }
  }

  def recover: Receive = {
    case XRecoverRes(xraworders) =>
      // cancel the previous auto-cancel scheduling
      autoSwitchBackToReceive.foreach(_.cancel)
      autoSwitchBackToReceive = None

      log.debug(s"recover next ${xraworders.size} orders")

      for {
        _ <- Future.sequence(xraworders.map(recoverOrder))
      } yield {

        autoSwitchBackToReceive = Option(
          context.system.scheduler.scheduleOnce(1.minute, self, XRecoverEnded())
        )

        sender ! XRecoverProcessed()
      }

    case msg: XRecoverEnded =>
      s"account manager `${entityName}` recovery completed (due to timeout: ${timeout})"
      context.become(receive)

    case msg: Any =>
      log.warning(s"message not handled during recovery")
      sender ! XError(
        ERR_REJECTED_DURING_RECOVERY,
        s"account manager `${entityName}` is being recovered"
      )
  }

  def receive: Receive = {

    case XRecoverRes(xraworders) =>
      val size = xraworders.size
      log.debug(s"recover next ${size} orders")

      for {
        _ <- Future.sequence(xraworders.map(recoverOrder))
      } yield {
        sender ! XRecoverProcessed()
      }

    case req: Any =>
      (MultiAccountManagerActor.extractAddress.lift)(req) match {
        case Some(address) => accountManagerActorFor(address) forward req
        case None =>
          throw ErrorException(ERR_INVALID_REQ, "req cannot be handlled")
      }
  }

  protected def accountManagerActorFor(address: String): ActorRef = {
    if (!accountManagerActors.contains(address)) {
      val newAccountActor =
        context.actorOf(Props(new AccountManagerActor()), address)
      accountManagerActors.add(address, newAccountActor)
    }
    accountManagerActors.get(address)
  }

  def recoverOrder(xraworder: XRawOrder) = {
    accountManagerActorFor(xraworder.owner) ? XSubmitOrderReq(
      xraworder.owner,
      Some(xraworder)
    )
  }

}
