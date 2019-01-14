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
import org.loopring.lightcone.actors.core.TransactionRecordActor
import org.loopring.lightcone.ethereum.event.EventExtractor
import org.loopring.lightcone.proto._

import scala.concurrent.{ExecutionContext, Future}

class TransferEventDispatcher(
    implicit
    timeout: Timeout,
    lookup: Lookup[ActorRef],
    extractor: EventExtractor[TransferEvent],
    val ec: ExecutionContext)
    extends NameBasedEventDispatcher[TransferEvent] {

  val names = Seq(TransactionRecordActor.name)

  override def derive(block: RawBlockData): Future[Seq[TransferEvent]] =
    Future {
      val items = block.txs zip block.receipts
      items.flatMap { item =>
        extractor
          .extract(item._1, item._2, block.timestamp)
          .flatMap(
            event => Seq(event.withOwner(event.from), event.withOwner(event.to))
          )
      }

    }
}
