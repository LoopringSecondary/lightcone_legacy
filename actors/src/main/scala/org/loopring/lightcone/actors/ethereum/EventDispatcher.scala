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
import org.loopring.lightcone.ethereum.event._
import org.loopring.lightcone.proto._
import scala.concurrent._

trait EventDispatcher[R <: AnyRef] {
  implicit val ec: ExecutionContext
  val extractor: EventExtractor[R]

  def targets: Seq[ActorRef]

  def derive(
      block: RawBlockData,
      events: Seq[R]
    ): Future[Seq[AnyRef]] =
    Future.successful(events)

  // Never override this method!!!
  def dispatch(block: RawBlockData): Future[Int] = {
    val items = block.txs zip block.receipts
    val events = items.map { item =>
      extractor.extract(item._1, item._2, block.timestamp)
    }.flatten.distinct
    for {
      derived <- derive(block, events)
      _ = derived.foreach { e =>
        targets.foreach(_ ! e)
      }
    } yield derived.size
  }
}

trait NameBasedEventDispatcher[R <: AnyRef] extends EventDispatcher[R] {
  val names: Seq[String]
  val lookup: Lookup[ActorRef]
  def targets: Seq[ActorRef] = names.map(lookup.get)
}
