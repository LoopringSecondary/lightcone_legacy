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
import akka.event.LoggingReceive
import com.typesafe.config.Config
import akka.util.Timeout
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.relayer.validator._
import io.lightcone.core._
import io.lightcone.proto._
import scala.concurrent.ExecutionContext

// Owner: Hongyu
object EntryPointActor {
  val name = "entrypoint"

  def start(
      implicit
      system: ActorSystem,
      config: Config,
      ec: ExecutionContext,
      timeout: Timeout,
      actors: Lookup[ActorRef]
    ) = {
    system.actorOf(Props(new EntryPointActor()), EntryPointActor.name)
  }
}

class EntryPointActor(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef])
    extends Actor
    with ActorLogging {

  import ErrorCode._

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
    case _: SubmitOrder.Req | _: CancelOrder.Req =>
      Some(MultiAccountManagerMessageValidator.name)

    case _: GetBalanceAndAllowances.Req =>
      Some(MultiAccountManagerMessageValidator.name)

    case _: GetBalance.Req | _: GetAllowance.Req | _: GetFilledAmount.Req =>
      Some(EthereumQueryMessageValidator.name)

    case _: JsonRpc.Request | _: JsonRpc.RequestWithHeight =>
      Some(EthereumAccessActor.name)

    case _: GetOrderbook.Req => Some(OrderbookManagerMessageValidator.name)

    case _: GetOrdersForUser.Req | _: GetTrades.Req | _: GetRings.Req =>
      Some(DatabaseQueryMessageValidator.name)

    case _: GetTransactionRecords.Req | _: GetTransactionRecordCount.Req =>
      Some(TransactionRecordMessageValidator.name)

    case _: GetMetadatas.Req => Some(MetadataRefresher.name)

    case _ => None
  }

}
