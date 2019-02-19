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
import io.lightcone.relayer.base._

trait EventDispatcher {
  def dispatch(evt: AnyRef)
}

class EventDispatcherActorImpl(actors: Lookup[ActorRef])
    extends EventDispatcher {
  var targets = Map.empty[Class[_], Set[String]]

  def register(
      cls: Class[_],
      actorNames: String*
    ) = {
    targets = targets + (cls -> (targets.getOrElse(cls, Set.empty[String]) ++ actorNames.toSet))
    this
  }

  def dispatch(evt: AnyRef) = {
    targets.getOrElse(evt.getClass, Set.empty).map(actors.get).foreach(_ ! evt)
  }

}
