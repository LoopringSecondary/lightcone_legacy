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

package org.loopring.lightcone.lib.cache.reader

import org.loopring.lightcone.lib.cache._
import org.loopring.lightcone.lib.cache.serializer._

import scala.concurrent._

final class ProtoToProtoCachedReader[R <: scalapb.GeneratedMessage with scalapb.Message[R], T <: scalapb.GeneratedMessage with scalapb.Message[T]](
    val underlying: Reader[R, T],
    genKey: R ⇒ Array[Byte]
)(
    implicit
    val ex: ExecutionContext,
    val underlyingCache: ByteArrayCache,
    cR: scalapb.GeneratedMessageCompanion[R],
    cT: scalapb.GeneratedMessageCompanion[T]
)
  extends CachedReader[R, T] {

  val cache = new ProtoToProtoCache[R, T](
    underlyingCache,
    new ProtoSerializer,
    genKey
  )
}
