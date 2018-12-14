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
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import scala.concurrent._

// main owner: 于红雨
object OrderHandlerActor extends ShardedEvenly {
  val name = "order_handler"

  def startShardRegion(orderService: OrderService)(
    implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef]
  ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new OrderHandlerActor(orderService)),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class OrderHandlerActor(orderService: OrderService)(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(OrderHandlerActor.name) {

  def accountManagerActor = actors.get(AccountManagerActor.name)

  //save order to db first, then send to AccountManager
  def receive: Receive = {
    case req: XCancelOrderReq ⇒
      (for {
        saveRes ← orderService.updateOrderStatus(req.id, req.status)
      } yield {
        saveRes match {
          case Left(value) ⇒
            XCancelOrderRes(req.id, req.hardCancel, value, req.status)
          case Right(value) ⇒
            accountManagerActor forward req
        }
      }) pipeTo sender

    case XSubmitRawOrderReq(Some(raworder)) ⇒
      (for {
        saveRes ← orderService.submitOrder(raworder)
        //todo：ERR_ORDER_ALREADY_EXIST PERS_ERR_DUPLICATE_INSERT 区别
        res ← saveRes.error match {
          case XErrorCode.ERR_NONE | XErrorCode.PERS_ERR_DUPLICATE_INSERT ⇒
            for {
              submitRes ← accountManagerActor ? XSubmitOrderReq(Some(raworder))
            } yield {
              submitRes match {
                case XSubmitOrderRes(XErrorCode.ERR_NONE, _) ⇒
                  XSubmitRawOrderRes(raworder.hash, XErrorCode.ERR_NONE)
                case XSubmitOrderRes(err, _) ⇒
                  XSubmitRawOrderRes(raworder.hash, err)
                case _ ⇒
                  XSubmitRawOrderRes(raworder.hash, XErrorCode.ERR_UNKNOWN)
              }
            }
          case err ⇒
            Future.successful(XSubmitRawOrderRes(raworder.hash, saveRes.error))
        }

      } yield res) pipeTo sender

  }
}
