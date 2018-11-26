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
import scala.concurrent._

trait CachedReader[R, T] extends Reader[R, T] {
  implicit val ex: ExecutionContext
  val underlying: Reader[R, T]
  val cache: Cache[R, T]

  def read(req: R): Future[Option[T]] = for {
    cached ← cache.get(req)
    result ← cached match {
      case t @ Some(_) ⇒ Future(t)
      case None        ⇒ underlying.read(req)
    }
  } yield result

  def read(reqs: Seq[R]): Future[Map[R, T]] = for {
    cached ← cache.get(reqs)
    cachedReqs = cached.keys.toSeq
    uncachedReqs = reqs.filter(r ⇒ !cachedReqs.contains(r))
    uncached ← underlying.read(uncachedReqs)
    _ ← cache.put(uncached)
    result = uncached ++ cached
  } yield result
}
