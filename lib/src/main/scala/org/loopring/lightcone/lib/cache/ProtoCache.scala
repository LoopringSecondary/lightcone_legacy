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

package org.loopring.lightcone.lib.cache

import org.loopring.lightcone.lib.cache.serializer._
import scala.concurrent._

trait ProtoCache[K, V <: scalapb.GeneratedMessage with scalapb.Message[V]]
  extends Cache[K, V] {
  implicit val ex: ExecutionContext

  val underlying: ByteArrayCache
  val serializer: ProtoSerializer[V]

  def keyToBytes(k: K): Array[Byte]

  def get(key: K): Future[Option[V]] = {
    underlying.get(keyToBytes(key))
      .map(_.map(serializer.fromBytes))
  }
  def get(keys: Seq[K]): Future[Map[K, V]] = {
    val keyMap = keys.map(k ⇒ keyToBytes(k) -> k).toMap
    underlying.get(keyMap.keys.toSeq).map {
      _.map {
        case (k, v) ⇒ keyMap(k) -> serializer.fromBytes(v)
      }
    }
  }
  def put(key: K, value: V) = {
    underlying.put(
      keyToBytes(key),
      serializer.toBytes(value)
    )
  }
}
