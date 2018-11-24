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

package org.loopring.lightcone.actors.deployment

import akka.actor._

trait ActorLookup {
  def getActor(name: String): ActorRef
}

class MapBasedActorLookup extends ActorLookup {

  private var actors = Map.empty[String, ActorRef]

  def getActor(name: String) = {
    assert(actors.contains(name))
    actors(name)
  }

  def addActor(name: String, ref: ActorRef) = {
    assert(!actors.contains(name))
    actors += name -> ref
  }
}
