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
import akka.util.Timeout
import akka.event.LoggingReceive
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.proto._

import scala.concurrent.ExecutionContext

object EntryPointActor {
  val name = "entrypoint"
}

class EntryPointActor()(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    actors: Lookup[ActorRef]
) extends Actor with ActorLogging {

  def receive = LoggingReceive {
    case msg: Any ⇒
      (findDestination.lift)(msg) match {
        case Some(dest) ⇒
          actors.get(dest) forward msg

        case None ⇒
          sender ! XError(
            code = XErrorCode.ERR_UNSUPPORTED_MES,
            message = s"unsupported message: $msg"
          )
          log.debug(s"unsupported msg: $msg")
      }
  }

  //TODO: 这里的问题是，如果原始数据，比如地址，不符合规范，那么可能被分配到错误的shard
  // 有一个做法是在每个sharding之前，有个前置的actor，专门做validaition。
  val findDestination: PartialFunction[Any, String] = {
    case _@ (
      XSubmitRawOrderReq |
      XCancelOrderReq) ⇒ OrderHandlerActor.name

    case _@ (
      XGetOrderbookReq) ⇒ OrderbookManagerActor.name
  }

}
