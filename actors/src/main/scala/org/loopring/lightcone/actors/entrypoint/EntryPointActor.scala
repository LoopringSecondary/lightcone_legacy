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

package org.loopring.lightcone.actors.entrypoint

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent.ExecutionContext

object EntryPointActor {
  val name = "entrypoint"
}

class EntryPointActor(
  )(
    implicit ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef])
    extends Actor
    with ActorLogging {

  def receive = LoggingReceive {
    case msg: Any =>
      findDestination(msg) match {
        case Some(dest) =>
          if (actors.contains(dest)) {
            actors.get(dest) forward msg
          } else {
            sender ! Error(ERR_INTERNAL_UNKNOWN, s"not found actor: $dest")
          }
        case None =>
          sender ! Error(ERR_UNSUPPORTED_MESSAGE, s"unsupported message: $msg")
          log.debug(s"unsupported msg: $msg")
      }
  }

  def findDestination(msg: Any): Option[String] = msg match {
    case _: SubmitOrderReq | _: CancelOrderReq =>
      Some(OrderHandlerMessageValidator.name)

    case _: GetBalanceAndAllowancesReq ⇒
      Some(MultiAccountManagerMessageValidator.name)

    case _: GetBalanceReq | _: GetAllowanceReq | _: GetFilledAmountReq ⇒
      Some(EthereumQueryMessageValidator.name)

    case _: XJsonRpcReq | _: XRpcReqWithHeight ⇒
      Some(EthereumAccessActor.name)

    case _: Orderbook.Get => Some(OrderbookManagerMessageValidator.name)

    case _: GetOrdersForUserReq | _: GetTradesReq =>
      Some(DatabaseQueryMessageValidator.name)

    case _ => None
  }

}
