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

package io.lightcone.relayer.base
import org.slf4s.Logging

// Owner: Daniel
trait Lookup[T] {
  def size(): Int
  def get(name: String): T

  def add(
      name: String,
      ref: T
    ): Lookup[T]

  def del(name: String): Lookup[T]
  def clear(): Lookup[T]

  def contains(name: String): Boolean

  def all(): Seq[T]
}

class MapBasedLookup[T] extends Lookup[T] with Logging {

  private var items = Map.empty[String, T]

  def size() = items.size

  def get(name: String) = {
    if (!items.contains(name)) {
      val err = s"Cannot find item with name $name"
      log.error(err)
      throw new IllegalStateException(err)
    }
    items(name)
  }

  def contains(name: String): Boolean = {
    items.contains(name)
  }

  def add(
      name: String,
      item: T
    ) = {
    assert(!contains(name))
    items += name -> item
    this
  }

  def del(name: String) = {
    items -= name
    this
  }

  def clear() = {
    items = Map.empty
    this
  }

  def all() = {
    items.values.toSeq
  }

}
