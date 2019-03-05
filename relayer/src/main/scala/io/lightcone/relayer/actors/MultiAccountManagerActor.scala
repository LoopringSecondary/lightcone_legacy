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
import akka.actor.SupervisorStrategy.{Escalate, Restart}
import akka.actor._
import akka.pattern.ask
import akka.serialization.Serialization
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.ethereum.event._
import io.lightcone.relayer.base._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data._
import io.lightcone.core._
import org.web3j.utils._
import kamon.metric._
import scala.concurrent._
import scala.concurrent.duration._

// Owner: Hongyu
object MultiAccountManagerActor extends DeployedAsShardedByAddress {

  import MarketMetadata.Status._

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
        if metadataManager.isMarketStatus(
          MarketPair(rawOrder.tokenS, rawOrder.tokenB),
          ACTIVE,
          READONLY
        ) =>
      rawOrder.owner

    case ActorRecover.RecoverOrderReq(Some(raworder))
        if metadataManager.isMarketStatus(
          MarketPair(raworder.tokenS, raworder.tokenB),
          ACTIVE,
          READONLY
        ) =>
      raworder.owner

    case req: CancelOrder.Req
        if req.marketPair.nonEmpty && metadataManager.isMarketStatus(
          req.getMarketPair,
          ACTIVE,
          READONLY
        ) =>
      req.owner

    case req: GetAccount.Req                      => req.address
    case req: AddressBalanceUpdatedEvent          => req.address
    case req: AddressBalanceAllowanceUpdatedEvent => req.address
    case req: AddressAllowanceUpdatedEvent        => req.address
    case req: CutoffEvent                         => req.owner // TODO:暂不支持broker
    case req: OrderFilledEvent                    => req.owner
    case req: OrdersCancelledOnChainEvent         => req.owner

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

  import ErrorCode._

  val metricName = s"multi_account_${entityId}"
  val count = KamonSupport.counter(metricName)
  val gauge = KamonSupport.gauge(metricName)
  val histo = KamonSupport.histogram(metricName)
  val timer = KamonSupport.timer(metricName)

  val getBalanceAndALlowanceCounter =
    KamonSupport.counter("mama.get-balance-and-allowance")

  val selfConfig = config.getConfig(MultiAccountManagerActor.name)

  val balanceRefreshIntervalSeconds =
    selfConfig.getInt("balance-allowance-refresh-interval-seconds")

  val skiprecover = selfConfig.getBoolean("skip-recover")

  log.info(s"=======> starting MultiAccountManagerActor ${self.path}")

  val maxRecoverDurationMinutes =
    selfConfig.getInt("max-recover-duration-minutes")

  val accountManagerActors = new MapBasedLookup[ActorRef]()

  val extractShardingObject =
    MultiAccountManagerActor.extractShardingObject.lift

  var autoSwitchBackToReady: Option[Cancellable] = None
  var recoverTimer: Option[StartedTimer] = None

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
    recoverTimer = Some(timer.refine("label" -> "recover").start)

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
            accountEntityId = entityId,
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

      recoverTimer.foreach(_.stop)
      recoverTimer = None

      val numOfAccounts = accountManagerActors.size
      gauge.refine("label" -> "num_accounts").set(numOfAccounts)
      histo.refine("label" -> "num_accounts").record(numOfAccounts)

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
    case req: MetadataChanged =>
      accountManagerActors.all() foreach { _ ! req }

    case req: Any => handleRequest(req)
  }

  // TODO(hongyu):以太坊的事件如 AddressBalanceUpdated和AddressAllowanceUpdated 不应该触发创建AccountManagerActor
  private def handleRequest(req: Any) = extractShardingObject(req) match {
    case Some(address) => {
      req match {
        case _: AddressBalanceUpdatedEvent | _: AddressAllowanceUpdatedEvent |
            _: AddressBalanceAllowanceUpdatedEvent | _: CutoffEvent |
            _: OrderFilledEvent =>
          if (accountManagerActors.contains(address))
            accountManagerActorFor(address) forward req

        case _ =>
          accountManagerActorFor(address) forward req
      }
    }
    case None =>
      sender ! Error(
        ERR_UNEXPECTED_ACTOR_MSG,
        s"$req cannot be handled by ${getClass.getName}"
      )
  }

  implicit private val balanceProvider = new BalanceAndAllowanceProvider {

    def getBalanceAndALlowance(
        address: String,
        token: String
      ): Future[(Long, BigInt, BigInt)] = {

      val t = timer.refine("label" -> "get_account").start
      @inline def ethereumQueryActor = actors.get(EthereumQueryActor.name)

      (for {
        res <- (ethereumQueryActor ? GetAccount.Req(address, Seq(token)))
          .mapAs[GetAccount.Res]
        accountBalance = res.accountBalance.getOrElse(AccountBalance())
        tb = accountBalance.tokenBalanceMap.getOrElse(
          token,
          AccountBalance.TokenBalance()
        )
        balance = BigInt(tb.balance.toByteArray)
        allowance = BigInt(tb.allowance.toByteArray)
      } yield (tb.block, balance, allowance)).andThen {
        case _ =>
          t.stop()
          count.refine("label" -> "get_account").increment()
      }
    }
  }

  private def accountManagerActorFor(address: String): ActorRef =
    this.synchronized {
      if (!accountManagerActors.contains(address)) {
        log.info(s"created new account manager for address $address")
        accountManagerActors.add(
          address,
          context.actorOf(
            Props(
              new AccountManagerActor(address, balanceRefreshIntervalSeconds)
            ),
            address
          )
        )
      }
      accountManagerActors.get(address)
    }

}
