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

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import io.lightcone.lib._
import io.lightcone.core._
import io.lightcone.relayer.base._
import io.lightcone.persistence._
import io.lightcone.relayer.data._
import scala.concurrent._

// Owner: Yongfeng
object OrderRecoverActor extends DeployedAsShardedWithMessageId {
  val name = "order_recover"

  val extractShardingObject: PartialFunction[Any, Long] = {
    case req: ActorRecover.RequestBatch => req.batchId.toLong
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
    startSharding(Props(new OrderRecoverActor()))
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
    extends InitializationRetryActor {
  import OrderStatus._
  import MarketMetadata.Status._

  val selfConfig = config.getConfig(OrderRecoverActor.name)
  val batchSize = selfConfig.getInt("batch-size")
  var batch: ActorRecover.RequestBatch = _
  var numOrders = 0L
  val multiAccountConfig = config.getConfig(MultiAccountManagerActor.name)
  val numOfShards = multiAccountConfig.getInt("num-of-shards")
  @inline def coordinator = actors.get(OrderRecoverCoordinator.name)
  @inline def mama = actors.get(MultiAccountManagerActor.name)

  val orderStatus = Set(STATUS_NEW, STATUS_PENDING, STATUS_PARTIALLY_FILLED)
  var accountEntityIds: Set[Long] = Set.empty
  var marketEntityIds: Set[Long] = Set.empty

  def ready: Receive = {
    case req: ActorRecover.RequestBatch =>
      log.info(s"started order recover - $req")
      println(s"received RequestBatch $req--${timeProvider.getTimeSeconds()}")
      batch = req

      sender ! batch // echo back to coordinator
      self ! ActorRecover.RetrieveOrders(0L)

      batch.requestMap.foreach {
        case (_, request) => {
          accountEntityIds += request.accountEntityId
          if (request.marketPair.nonEmpty) {
            val marketHashId =
              MarketManagerActor.getEntityId(request.marketPair.get)
            marketEntityIds += marketHashId
          }
        }
      }
      log.debug(
        s"the request params of batch: ${batchSize}, ${marketEntityIds}, ${accountEntityIds}"
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
          metadataManager.isMarketStatus(
            MarketPair(o.tokenS, o.tokenB),
            ACTIVE,
            READONLY
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
                println(s"sending to ${actor.path.toSerializationFormat}")
                actor ! ActorRecover.Finished(false)
              }

            println(s"recover finished ${batch.requestMap}")

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
        "the request params of retrieveOrders: ",
        s"${batchSize}, ${lastOrderSeqId}, ${orderStatus}, ",
        s"${marketEntityIds}, ${accountEntityIds}"
      )

      dbModule.orderService.getOrdersForRecover(
        orderStatus,
        marketEntityIds,
        accountEntityIds,
        CursorPaging(lastOrderSeqId, batchSize)
      )
    } else {
      Future.successful(Seq.empty)
    }
  }

}
