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
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.lib.{ErrorException, _}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent._

// main owner: 于红雨
object OrderHandlerActor extends ShardedEvenly {
  val name = "order_handler"

  def startShardRegion(
    )(
      implicit system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeProvider: TimeProvider,
      timeout: Timeout,
      actors: Lookup[ActorRef],
      dbModule: DatabaseModule
    ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderHandlerActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class OrderHandlerActor(
  )(
    implicit val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val dbModule: DatabaseModule)
    extends ActorWithPathBasedConfig(OrderHandlerActor.name) {

  def mammv: ActorRef =
    actors.get(MultiAccountManagerMessageValidator.name)

  //save order to db first, then send to AccountManager
  def receive: Receive = {
    case req: XCancelOrderReq ⇒
      (for {
        cancelRes <- dbModule.orderService.markOrderSoftCancelled(Seq(req.id))
      } yield {
        cancelRes.headOption match {
          case Some(res) ⇒
            if (res.order.isEmpty)
              throw ErrorException(ERR_ORDER_NOT_EXIST, "no such order")
            req.copy(owner = res.order.get.owner)
          case None ⇒
            throw ErrorException(ERR_ORDER_NOT_EXIST, "no such order")
        }
      }) forwardTo (mammv, sender)

    case XSubmitOrderReq(Some(raworder)) ⇒
      (for {
        //todo：ERR_ORDER_ALREADY_EXIST PERS_ERR_DUPLICATE_INSERT 区别
        saveRes <- dbModule.orderService.saveOrder(raworder)
      } yield {
        saveRes match {
          case Right(errCode) =>
            throw ErrorException(errCode, s"failed to submit order: $raworder")
          case Left(resRawOrder) =>
            XSubmitSimpleOrderReq(resRawOrder.owner, Some(resRawOrder))
        }
      }) forwardTo (mammv, sender)
  }
}
