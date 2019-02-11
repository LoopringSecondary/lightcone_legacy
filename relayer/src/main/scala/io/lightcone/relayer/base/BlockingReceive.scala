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
import io.lightcone.relayer.data._
import scala.concurrent._

// Owner: dongw
trait BlockingReceive { me: Actor with Stash =>
  implicit val ec: ExecutionContext

  private val SYNC_NOTIFY = Notify("sync")

  // Guarantees all futures that modifies this actor's internal state will force
  // this actor to become `blockingReceive` and wait for a SYNC_NOTIFY message
  // to become `normalReceive` again.
  def blocking(future: => Future[_]) = {
    context.become(blockingReceive)
    for {
      result <- future
      _ = self ! SYNC_NOTIFY
    } yield result
  }

  private val blockingReceive: Receive = {
    case SYNC_NOTIFY =>
      context.unbecome()
      unstashAll()

    case _ => stash()
  }
}
