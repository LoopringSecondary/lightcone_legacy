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

trait EventDispatcher[T] {

  def register(
      cls: Class[_],
      t: T*
    )
  def dispatch(evt: scalapb.GeneratedMessage)
}

class EventDispatcherActorImpl extends EventDispatcher[ActorRef] {
  var targets = Map.empty[Class[_], Seq[ActorRef]]

  def register(
      cls: Class[_],
      t: ActorRef*
    ) = {
    targets = targets + (cls -> (targets.getOrElse(cls, Seq.empty[ActorRef]) ++ t))
  }

  def dispatch(evt: scalapb.GeneratedMessage) = {
    targets.getOrElse(evt.getClass, Seq.empty).foreach(_ ! evt)
  }

}
