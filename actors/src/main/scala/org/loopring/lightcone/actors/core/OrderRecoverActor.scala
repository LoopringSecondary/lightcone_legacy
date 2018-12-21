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
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.core.account._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.proto.XErrorCode._
import org.loopring.lightcone.proto.XOrderStatus._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import akka.cluster.sharding.ShardRegion.HashCodeMessageExtractor
import scala.concurrent._

// main owner: 杜永丰
object OrderRecoverActor extends ShardedEvenly {
  val name = "order_recover"

  override protected val messageExtractor =
    new HashCodeMessageExtractor(numOfShards) {
      override def entityId(message: Any) = message match {
        case req: XRecoverReq =>
          name + "_batch_" + req.batchId
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
      actors: Lookup[ActorRef]
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
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef])
    extends ActorWithPathBasedConfig(OrderRecoverActor.name) {

  def mama: ActorRef = actors.get(MultiAccountManagerActor.name)

  def receive: Receive = {
    case req: XRecoverReq =>
      log.info(s"started order recover - $req")
      context.become(recovering)
  }

  def recovering: Receive = {

    case _ =>
  }

}
