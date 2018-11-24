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

trait PropsLookup {
  def getProps(name: String): Props
  def addProps(name: String, ref: Props): Unit
  def removeProps(name: String): Unit
}

class MapBasedPropsLookup extends PropsLookup {

  private var actors = Map.empty[String, Props]

  def getProps(name: String) = {
    assert(actors.contains(name))
    actors(name)
  }

  def addProps(name: String, ref: Props) = {
    assert(!actors.contains(name))
    actors += name -> ref
  }

  def removeProps(name: String) = {
    actors -= name
  }
}
