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

import scala.concurrent._

private[cache] final class ProtoCacheSerializer[
    T <: scalapb.GeneratedMessage with scalapb.Message[T]
  ](
    implicit
    c: scalapb.GeneratedMessageCompanion[T])
    extends CacheSerializer[T] {

  def toBytes(obj: T): Array[Byte] = obj.toByteArray
  def fromBytes(bytes: Array[Byte]): T = c.parseFrom(bytes)
}

trait ProtoCache[K, V <: scalapb.GeneratedMessage with scalapb.Message[V]]
    extends Cache[K, V] {

  val underlying: Cache[String, Array[Byte]]
  implicit val c: scalapb.GeneratedMessageCompanion[V]
  private val serializer = new ProtoCacheSerializer[V]

  def keyToString(k: K): String

  def get(keys: Seq[K]): Future[Map[K, V]] = {
    val keyMap = keys.map(k => keyToString(k) -> k).toMap
    for {
      cached <- underlying.get(keyMap.keys.toSeq)
      res = cached.map {
        case (k, v) => keyMap(k) -> serializer.fromBytes(v)
      }
    } yield res
  }

  def del(keys: Seq[K]): Future[Unit] =
    underlying.del(keys.map(keyToString))

  def put(
      keyValues: Map[K, V],
      expiry: Long
    ): Future[Boolean] =
    underlying.put(keyValues.map {
      case (k, v) => keyToString(k) -> serializer.toBytes(v)
    }, expiry)
}

final class StringToProtoCache[
    V <: scalapb.GeneratedMessage with scalapb.Message[V]
  ](val underlying: Cache[String, Array[Byte]]
  )(
    implicit
    val ex: ExecutionContext,
    val c: scalapb.GeneratedMessageCompanion[V])
    extends ProtoCache[String, V] {
  @inline def keyToString(key: String) = key
}

final class ByteArrayToProtoCache[
    V <: scalapb.GeneratedMessage with scalapb.Message[V]
  ](val underlying: Cache[String, Array[Byte]]
  )(
    implicit
    val ex: ExecutionContext,
    val c: scalapb.GeneratedMessageCompanion[V])
    extends ProtoCache[Array[Byte], V] {
  @inline def keyToString(key: Array[Byte]) = new String(key)
}
