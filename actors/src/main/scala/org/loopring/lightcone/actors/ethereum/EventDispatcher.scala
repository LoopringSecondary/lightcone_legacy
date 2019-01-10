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
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.ethereum.event.EventExtractor

import org.loopring.lightcone.proto.RawBlockData

abstract class EventDispatcher[R, T](implicit extractor: EventExtractor[R]) {

  def derive(event: R): Seq[T]
  def targets: Seq[ActorRef]

  def dispatch(block: RawBlockData) {
    (block.txs zip block.receipts).foreach { item =>
      extractor.extract(item._1, item._2, block.timestamp).foreach { e =>
        derive(e).foreach { e =>
          targets.foreach(_ ! e)
        }
      }
    }
  }
}

trait NonDerivable[R] { self: EventDispatcher[R, R] =>
  def derive(event: R) = Seq(event)
}

abstract class NameBasedEventDispatcher[R, T](
    names: Seq[String]
  )(
    implicit
    extractor: EventExtractor[R],
    lookup: Lookup[ActorRef])
    extends EventDispatcher[R, T] {
  def targets: Seq[ActorRef] = names.map(lookup.get)
}
