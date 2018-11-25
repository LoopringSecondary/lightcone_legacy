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

trait Lookup[T] {
  def size(): Int
  def get(name: String): T
  def add(name: String, ref: T): Unit
  def del(name: String): Unit
  def clear(): Unit
}

class MapBasedLookup[T] extends Lookup[T] {

  private var items = Map.empty[String, T]

  def size() = items.size

  def get(name: String) = {
    assert(items.contains(name))
    items(name)
  }

  def add(name: String, item: T) = {
    assert(!items.contains(name))
    items += name -> item
  }

  def del(name: String) = {
    items -= name
  }

  def clear() = {
    items = Map.empty
  }
}
