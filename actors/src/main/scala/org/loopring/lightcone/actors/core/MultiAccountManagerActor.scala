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
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor._
import akka.cluster.sharding._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base.DustOrderEvaluator
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// Owner: Hongyu
object MultiAccountManagerActor extends ShardedByAddress {
  val name = "multi_account_manager"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dustEvaluator: DustOrderEvaluator,
      dbModule: DatabaseModule,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")

    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new MultiAccountManagerActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }

  // 如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  val extractAddress: PartialFunction[Any, String] = {
    case req: SubmitOrder.Req =>
      req.rawOrder
        .map(_.owner)
        .getOrElse {
          throw ErrorException(
            ERR_UNEXPECTED_ACTOR_MSG,
            "SubmitOrder.Req.rawOrder must be nonEmpty."
          )
        }

    case ActorRecover.RecoverOrderReq(Some(raworder)) => raworder.owner
    case req: CancelOrder.Req                         => req.owner
    case req: GetBalanceAndAllowances.Req             => req.address
    case req: AddressBalanceUpdated                   => req.address
    case req: AddressAllowanceUpdated                 => req.address
    case req: CutoffEvent                             => req.owner //todo:暂不支持broker
    case req: OrderFilledEvent                        => req.owner
  }
}

class MultiAccountManagerActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dustEvaluator: DustOrderEvaluator,
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(MultiAccountManagerActor.name)
    with ActorLogging {

  log.info(s"=======> starting MultiAccountManagerActor ${self.path}")

  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val accountManagerActors = new MapBasedLookup[ActorRef]()
  var autoSwitchBackToReceive: Option[Cancellable] = None
  val extractAddress = MultiAccountManagerActor.extractAddress.lift

  //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
  //todo: 完成recovery后，需要再次测试异常恢复情况
  override val supervisorStrategy =
    AllForOneStrategy() {
      case e: Exception =>
        log.error(e.getMessage)
        accountManagerActors.all().foreach(_ ! PoisonPill)
        Escalate
      case e =>
        Restart
    }

  override def preStart(): Unit = {
    super.preStart()

    autoSwitchBackToReceive = Some(
      context.system.scheduler
        .scheduleOnce(
          maxRecoverDurationMinutes.minute,
          self,
          ActorRecover.Finished(true)
        )
    )

    if (skiprecover) {
      log.warning(s"actor recover skipped: ${self.path}")
    } else {

      log.debug(s"actor recover started: ${self.path}")
      actors.get(OrderRecoverCoordinator.name) !
        ActorRecover.Request(addressShardingEntity = entityId)

      context.become(recover)
    }
  }

  def recover: Receive = {

    case req: ActorRecover.RecoverOrderReq => handleRequest(req)

    case ActorRecover.Finished(timeout) =>
      s"multi-account manager ${entityId} recover completed (timeout=${timeout})"
      context.become(receive)

    case msg: Any =>
      log.warning(s"message not handled during recover")
      sender ! Error(
        ERR_REJECTED_DURING_RECOVER,
        s"account manager ${entityId} is being recovered"
      )
  }

  def receive: Receive = {
    case req: Any => handleRequest(req)
  }

  //todo(hongyu):以太坊的事件如 AddressBalanceUpdated和AddressAllowanceUpdated 不应该触发创建AccountManagerActor
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
