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
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.ethereum.event._
import org.loopring.lightcone.proto._
import akka.util.Timeout
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.utils.TokenMetadataRefresher

import scala.concurrent._

abstract class EventDispatcher[R <: AnyRef](
    implicit
    extractor: EventExtractor[R]) {
  implicit val ec: ExecutionContext

  def derive(event: R): Future[Seq[AnyRef]] = Future.successful(Seq(event))

  def targets: Seq[ActorRef]

  // Never override this method!!!
  def dispatch(block: RawBlockData): Future[Int] = {
    val items = block.txs zip block.receipts
    for {
      events: Seq[R] <- Future
        .sequence(items.map { item =>
          extractor.extract(item._1, item._2, block.timestamp)
        })
        .map(_.flatten)

      derived: Seq[AnyRef] <- Future.sequence(events.map(derive(_)))
      _ = derived.foreach { e =>
        targets.foreach(_ ! e)
      }

    } yield derived.size
  }
}

abstract class NameBasedEventDispatcher[R <: AnyRef](
    implicit
    extractor: EventExtractor[R],
    lookup: Lookup[ActorRef])
    extends EventDispatcher[R] {
  val names: Seq[String]
  def targets: Seq[ActorRef] = names.map(lookup.get)
}
