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

package io.lightcone.lib.cache

import scala.concurrent._
import com.google.protobuf.ByteString

trait ProtoCache[K, V <: scalapb.GeneratedMessage with scalapb.Message[V]]
    extends Cache[K, V] {

  val underlying: Cache[String, Array[Byte]]
  val domain: String
  implicit val c: scalapb.GeneratedMessageCompanion[V]

  def keyToString(k: K): String

  private def k2S(k: K) = {
    if (domain.isEmpty) keyToString(k)
    else s"${domain}@${keyToString(k)}"
  }

  def get(keys: Seq[K]): Future[Map[K, V]] = {
    val keyMap = keys.map(k => k2S(k) -> k).toMap
    for {
      cached <- underlying.get(keyMap.keys.toSeq)
      res = cached.map {
        case (k, v) => keyMap(k) -> c.parseFrom(v)
      }
    } yield res
  }

  def del(keys: Seq[K]): Future[Unit] =
    underlying.del(keys.map(k2S))

  def put(
      keyValues: Map[K, V],
      expiry: Long
    ): Future[Boolean] =
    underlying.put(keyValues.map {
      case (k, v) => k2S(k) -> v.toByteArray
    }, expiry)
}

object ProtoCache {

  def apply[V <: scalapb.GeneratedMessage with scalapb.Message[V]](
      domain: String
    )(
      implicit
      underlying: Cache[String, Array[Byte]],
      ec: ExecutionContext,
      c: scalapb.GeneratedMessageCompanion[V]
    ) = new AnyToProtoCache(domain)

  final class AnyToProtoCache[
      V <: scalapb.GeneratedMessage with scalapb.Message[V]
    ](val domain: String = ""
    )(
      implicit
      val underlying: Cache[String, Array[Byte]],
      val ec: ExecutionContext,
      val c: scalapb.GeneratedMessageCompanion[V])
      extends ProtoCache[Any, V] {

    def keyToString(key: Any) = key match {
      case k: Array[Byte]           => new String(k)
      case k: String                => k
      case k: ByteString            => k.toStringUtf8
      case k: BigInt                => new String(k.toByteArray)
      case k: scalapb.GeneratedEnum => s"${k.getClass.getSimpleName}${k.value}"
      case k: Int                   => s"I${k}"
      case k: Long                  => s"L${k}"
      case k: Double                => s"D${k}"
      case k: Float                 => s"F${k}"
      case k =>
        throw new Exception(s"unsupported key type: ${k.getClass.getName}")
    }
  }
}
