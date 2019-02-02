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
import akka.cluster.sharding._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.proto._
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor
import org.loopring.lightcone.core.base.MetadataManager
import org.loopring.lightcone.persistence.DatabaseModule
import scala.concurrent._

// Owner: Yongfeng
object OrderRecoverActor extends ShardedEvenly {
  val name = "order_recover"

  override protected val messageExtractor =
    new HashCodeMessageExtractor(numOfShards) {
      override def entityId(message: Any) = message match {
        case req: ActorRecover.RequestBatch =>
          name + "_batch" + req.batchId
        case e: Any =>
          throw new Exception(s"$e not expected by OrderRecoverActor")
      }
    }

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      metadataManager: MetadataManager,
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    val roleOpt = if (deployActorsIgnoringRoles) None else Some(name)
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderRecoverActor()),
      settings = ClusterShardingSettings(system).withRole(roleOpt),
      messageExtractor = messageExtractor
    )
  }
}

class OrderRecoverActor(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    dbModule: DatabaseModule,
    metadataManager: MetadataManager)
    extends ActorWithPathBasedConfig(OrderRecoverActor.name) {
  import OrderStatus._

  val batchSize = selfConfig.getInt("batch-size")
  var batch: ActorRecover.RequestBatch = _
  var numOrders = 0L
  val multiAccountConfig = config.getConfig(MultiAccountManagerActor.name)
  val numOfShards = multiAccountConfig.getInt("num-of-shards")
  def coordinator = actors.get(OrderRecoverCoordinator.name)
  def mama = actors.get(MultiAccountManagerActor.name)

  val orderStatus = Set(STATUS_NEW, STATUS_PENDING, STATUS_PARTIALLY_FILLED)
  var accountShardIds: Set[Int] = Set.empty
  var marketHashIds: Set[Int] = Set.empty

  def ready: Receive = {
    case req: ActorRecover.RequestBatch =>
      log.info(s"started order recover - $req")
      batch = req

      sender ! batch // echo back to coordinator
      self ! ActorRecover.RetrieveOrders(0L)

      batch.requestMap.foreach {
        case (_, request) => {
          if ("" != request.addressShardingEntity)
            accountShardIds += request.addressShardingEntity.toInt
          if (request.marketPair.nonEmpty) {
            val marketHashId =
              MarketManagerActor.getEntityId(request.marketPair.get).toInt
            marketHashIds += marketHashId
          }
        }
      }
      log.debug(
        s"the request params of batch: ${batchSize}, ${marketHashIds}, ${accountShardIds}"
      )

      context.become(recovering)
  }

  def recovering: Receive = {
    case ActorRecover.CancelFor(requester) =>
      batch =
        batch.copy(requestMap = batch.requestMap.filterNot(_._1 == requester))

      sender ! batch // echo back to coordinator
      sender ! ActorRecover.Finished(false)
      context.stop(self)

    case ActorRecover.RetrieveOrders(lastOrderSeqId) =>
      for {
        orders <- retrieveOrders(batchSize, lastOrderSeqId)
        lastOrderSeqIdOpt = orders.lastOption.map(_.sequenceId)
        // filter unsupported markets
        availableOrders = orders.filter { o =>
          metadataManager.isMarketActiveOrReadOnly(
            MarketPair(o.tokenS, o.tokenB)
          )
        }
        _ <- if (availableOrders.nonEmpty) {
          val reqs = availableOrders.map { order =>
            ActorRecover.RecoverOrderReq(Some(order))
          }
          log.info(
            s"--> batch#${batch.batchId} recovering ${orders.size} orders (total=${numOrders})..."
          )
          Future.sequence(reqs.map(mama ? _))
        } else {
          Future.unit
        }
      } yield {
        numOrders += orders.size

        lastOrderSeqIdOpt match {
          case Some(lastOrderSeqId) =>
            self ! ActorRecover.RetrieveOrders(lastOrderSeqId)

          case None =>
            coordinator ! ActorRecover.Finished(false)

            batch.requestMap.keys.toSeq
              .map(resolveActorRef)
              .foreach { actor =>
                actor ! ActorRecover.Finished(false)
              }

            context.stop(self)
        }
      }
  }

  def resolveActorRef(actorRefStr: String): ActorRef = {
    context.system
      .asInstanceOf[ExtendedActorSystem]
      .provider
      .resolveActorRef(actorRefStr)
  }

  // This method returns a list of orders to recover, based on the current batch
  // parameters, the batch size, and the last order sequence id.
  // The last order in the returned list should be the most up-to-date one.
  def retrieveOrders(
      batchSize: Int,
      lastOrderSeqId: Long
    ): Future[Seq[RawOrder]] = {
    if (batch.requestMap.nonEmpty) {
      log.debug(
        s"the request params of retrieveOrders: ${batchSize}, ${lastOrderSeqId}, ${orderStatus}, ${marketHashIds}, ${accountShardIds}"
      )
      dbModule.orderService.getOrdersForRecover(
        orderStatus,
        marketHashIds,
        accountShardIds,
        CursorPaging(lastOrderSeqId, batchSize)
      )
    } else {
      Future.successful(Seq.empty)
    }
  }

}
