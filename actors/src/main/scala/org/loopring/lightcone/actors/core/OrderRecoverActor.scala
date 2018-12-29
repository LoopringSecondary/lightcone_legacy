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
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.proto._
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor
import org.loopring.lightcone.persistence.DatabaseModule
import scala.concurrent._

// main owner: 杜永丰
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

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule,
      supportedMarkets: SupportedMarkets
    ): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderRecoverActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      messageExtractor = messageExtractor
    )
  }
}

class OrderRecoverActor(
  )(
    implicit val config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef],
    dbModule: DatabaseModule,
    supportedMarkets: SupportedMarkets)
    extends ActorWithPathBasedConfig(OrderRecoverActor.name) {

  val batchSize = selfConfig.getInt("batch-size")
  var batch: ActorRecover.RequestBatch = _
  var numOrders = 0L
  def coordinator = actors.get(OrderRecoverCoordinator.name)
  def mama = actors.get(MultiAccountManagerActor.name)

  def receive: Receive = {
    case req: ActorRecover.RequestBatch =>
      log.info(s"started order recover - $req")
      batch = req

      sender ! batch // echo back to coordinator
      self ! ActorRecover.RetrieveOrders(0L)

      context.become(recovering)
  }

  def recovering: Receive = {
    case ActorRecover.CancelFor(requester) =>
      batch =
        batch.copy(requestMap = batch.requestMap.filterNot(_._1 == requester))

      sender ! batch // echo back to coordinator

    case ActorRecover.RetrieveOrders(lastOrderSeqId) =>
      for {
        orders <- retrieveOrders(batchSize, lastOrderSeqId)
        lastOrderSeqIdOpt = orders.lastOption.map(_.sequenceId)
        // filter unsupported markets
        multiAccountConfig = config.getConfig(MultiAccountManagerActor.name)
        numOfShards = multiAccountConfig.getInt("num-of-shards")
        availableOrders = orders.filter { o =>
          supportedMarkets.contains(MarketId(o.tokenS, o.tokenB))
        }.map { o =>
          val marketHash =
            MarketHashProvider.convert2Hex(o.tokenS, o.tokenB)
          val marketId =
            MarketId(primary = o.tokenS, secondary = o.tokenB)
          o.copy(
            marketHash = marketHash,
            marketHashId = MarketManagerActor.getEntityId(marketId).toInt,
            addressShardId = MultiAccountManagerActor
              .getEntityId(o.owner, numOfShards)
              .toInt
          )
        }
        reqs = availableOrders.map { order =>
          ActorRecover.RecoverOrderReq(Some(order))
        }
        _ = log.info(
          s"--> batch#${batch.batchId} recovering ${orders.size} orders (total=${numOrders})..."
        )
        _ <- Future.sequence(reqs.map(mama ? _))
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
      var addressShardIds: Set[Int] = Set.empty
      var marketHashIds: Set[Int] = Set.empty
      batch.requestMap.foreach {
        case (_, request) => {
          if ("" != request.addressShardingEntity)
            addressShardIds += request.addressShardingEntity.toInt
          if (request.marketId.nonEmpty) {
            val marketHashId =
              MarketManagerActor.getEntityId(request.marketId.get).toInt
            marketHashIds += marketHashId
          }
        }
      }
      val status = Set(
        OrderStatus.STATUS_NEW,
        OrderStatus.STATUS_PENDING,
        OrderStatus.STATUS_PARTIALLY_FILLED
      )
      dbModule.orderService.getOrdersForRecover(
        status,
        marketHashIds,
        addressShardIds,
        CursorPaging(lastOrderSeqId, batchSize)
      )
    } else {
      Future.successful(Seq.empty)
    }
  }

}
