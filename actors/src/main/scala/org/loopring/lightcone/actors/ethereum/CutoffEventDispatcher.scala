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

package org.loopring.lightcone.actors.ethereum

import akka.actor.ActorRef
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.ethereum.event.EventExtractor
import org.loopring.lightcone.proto.CutoffEvent

import scala.concurrent.ExecutionContext

class CutoffEventDispatcher(
    implicit
    timeout: Timeout,
    lookup: Lookup[ActorRef],
    extractor: EventExtractor[CutoffEvent],
    val ec: ExecutionContext)
    extends NameBasedEventDispatcher[CutoffEvent] {

  val names = Seq(
    TransactionRecordActor.name,
    OrderCutoffHandlerActor.name,
    MultiAccountManagerActor.name
  )

}
