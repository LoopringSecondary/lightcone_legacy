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

package io.lightcone.relayer.ethereum

import akka.actor.ActorRef
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.ethereum.event._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent._

trait EventDispatcher[R <: AnyRef] {
  implicit val ec: ExecutionContext
  val extractor: EventExtractor[R]
  def targets: Seq[ActorRef]

  def dispatch(block: RawBlockData): Future[Int] = {
    for {
      events <- extractor.extract(block)
      _ = events.foreach { e =>
        targets.foreach(_ ! e)
      }
    } yield events.size
  }
}

class NameBasedEventDispatcher[R <: AnyRef](
    names: Seq[String],
    actors: Lookup[ActorRef]
  )(
    implicit
    val ec: ExecutionContext,
    val extractor: EventExtractor[R])
    extends EventDispatcher[R] {
  def targets: Seq[ActorRef] = names.map(actors.get)
}
