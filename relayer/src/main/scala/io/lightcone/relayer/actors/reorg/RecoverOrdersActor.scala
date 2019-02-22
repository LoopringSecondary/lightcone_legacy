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
import io.lightcone.relayer.base._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent._
import org.slf4s.Logging

class RecoverOrdersActor(
  )(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef])
    extends Actor
    with Logging {

  val mama = actors.get(MultiAccountManagerActor.name)
  val query = actors.get(EthereumQueryActor.name)

  private val NEXT = Notify("next")
  var orderIds: Seq[String] = _

  def receive = {
    case ChainReorganizationImpact(orderIds_, _) =>
      log.debug(s"started recovering orders [size=${orderIds.size}]")
      orderIds = orderIds_
      self ! NEXT

    case NEXT =>
      orderIds.headOption match {
        case None =>
          context.stop(self)
          log.debug("finished recovering orders")

        case Some(orderId) =>
          orderIds = orderIds.tail
          // TODO(yongfeng): get the order with the given id, ignoring the order's status in DB.
          val rawOrderOpt: Option[RawOrder] = ???

          rawOrderOpt match {
            case None => self ! NEXT

            case Some(rawOrder) =>
              for {
                _ <- mama ? ActorRecover.RecoverOrderReq(Some(rawOrder))
                _ = log.debug(s"order recovered ${rawOrder.hash}")
                _ = self ! NEXT
              } yield Unit
          }
      }
  }
}
