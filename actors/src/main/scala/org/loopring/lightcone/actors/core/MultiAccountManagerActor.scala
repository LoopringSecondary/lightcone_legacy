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
import akka.pattern.ask
import akka.serialization.Serialization
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base.{DustOrderEvaluator, MetadataManager}
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import org.web3j.utils._
import scala.concurrent._
import scala.concurrent.duration._

// Owner: Hongyu
object MultiAccountManagerActor extends DeployedAsShardedByAddress {
  val name = "multi_account_manager"

  var metadataManager: MetadataManager = _

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
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    this.metadataManager = metadataManager
    startSharding(Props(new MultiAccountManagerActor()))
  }

  //如果message不包含一个有效的address，就不做处理，不要返回“默认值”
  //在validator 中已经做了拦截，该处再做一次，用于recover过滤
  val extractShardingObject: PartialFunction[Any, String] = {
    case SubmitOrder.Req(Some(rawOrder))
        if metadataManager.isMarketActiveOrReadOnly(
          MarketPair(rawOrder.tokenS, rawOrder.tokenB)
        ) =>
      rawOrder.owner

    case ActorRecover.RecoverOrderReq(Some(raworder))
        if metadataManager.isMarketActiveOrReadOnly(
          MarketPair(raworder.tokenS, raworder.tokenB)
        ) =>
      raworder.owner

    case req: CancelOrder.Req
        if req.marketPair.nonEmpty && metadataManager.isMarketActiveOrReadOnly(
          req.getMarketPair
        ) =>
      req.owner

    case req: GetBalanceAndAllowances.Req => req.address
    case req: AddressBalanceUpdated       => req.address
    case req: AddressAllowanceUpdated     => req.address
    case req: CutoffEvent                 => req.owner // TODO:暂不支持broker
    case req: OrderFilledEvent            => req.owner

    case Notify(KeepAliveActor.NOTIFY_MSG, address) =>
      Numeric.toHexStringWithPrefix(BigInt(address).bigInteger)
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
    val dbModule: DatabaseModule,
    val metadataManager: MetadataManager)
    extends InitializationRetryActor
    with ShardingEntityAware
    with ActorLogging {

  log.info(s"=======> starting MultiAccountManagerActor ${self.path}")

  val selfConfig = config.getConfig(MultiAccountManagerActor.name)

  val skiprecover = selfConfig.getBoolean("skip-recover")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val accountManagerActors = new MapBasedLookup[ActorRef]()

  val extractShardingObject =
    MultiAccountManagerActor.extractShardingObject.lift

  var autoSwitchBackToReady: Option[Cancellable] = None

  //shardingActor对所有的异常都会重启自己，根据策略，也会重启下属所有的Actor
  // TODO: 完成recovery后，需要再次测试异常恢复情况
  override val supervisorStrategy =
    AllForOneStrategy() {
      case e: Exception =>
        log.error(e.getMessage)
        accountManagerActors.all().foreach(_ ! PoisonPill)
        Escalate
      case e =>
        Restart
    }

  override def initialize() = {
    if (skiprecover) Future.successful {
      log.debug(s"actor recover skipped: ${self.path}")
      becomeReady()
    } else {
      log.debug(s"actor recover started: ${self.path}")
      context.become(recover)
      for {
        _ <- actors.get(EthereumQueryActor.name) ? Notify("echo") //检测以太坊准备好之后才发起恢复请求
        _ <- actors.get(OrderRecoverCoordinator.name) ?
          ActorRecover.Request(
            addressShardingEntity = entityId.toString,
            sender = Serialization.serializedActorPath(self)
          )
      } yield {
        autoSwitchBackToReady = Some(
          context.system.scheduler
            .scheduleOnce(
              maxRecoverDurationMinutes.minute,
              self,
              ActorRecover.Finished(true)
            )
        )
      }
    }
  }

  def recover: Receive = {
    case req: ActorRecover.RecoverOrderReq => handleRequest(req)

    case ActorRecover.Finished(timeout) =>
      s"multi-account manager ${entityId} recover completed (timeout=${timeout})"
      becomeReady()

    case msg: Any =>
      log.warning(s"message not handled during recover, ${msg}, ${sender}")
      //sender 是自己时，不再发送Error信息
      if (sender != self) {
        sender ! Error(
          ERR_REJECTED_DURING_RECOVER,
          s"account manager ${entityId} is being recovered"
        )
      }
  }

  def ready: Receive = {
    case req: Any => handleRequest(req)
  }

  // TODO(hongyu):以太坊的事件如 AddressBalanceUpdated和AddressAllowanceUpdated 不应该触发创建AccountManagerActor
  private def handleRequest(req: Any) = extractShardingObject(req) match {
    case Some(address) => {
      req match {
        case _: AddressBalanceUpdated | _: AddressAllowanceUpdated |
            _: CutoffEvent | _: OrderFilledEvent =>
          if (accountManagerActors.contains(address))
            accountManagerActorFor(address) forward req
        case _ => accountManagerActorFor(address) forward req
      }
    }
    case None =>
      sender ! Error(
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
