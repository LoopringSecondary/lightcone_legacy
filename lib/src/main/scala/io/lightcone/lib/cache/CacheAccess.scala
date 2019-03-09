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

class CacheAccess[K, V](cache: Cache[K, V]) {

  def get(
      key: K
    )(load: K => Future[Option[V]],
      expiry: Long
    )(
      implicit
      ec: ExecutionContext
    ): Future[Option[V]] = {

    def _load(keys: Seq[K]): Future[Map[K, V]] =
      for {
        loaded <- load(keys(0))
        map = {
          if (loaded.isEmpty) Map.empty[K, V]
          else Map(keys(0) -> loaded.get)
        }
      } yield map

    for {
      map <- get(Seq(key))(_load, expiry)
      res = map.get(key)
    } yield res
  }

  def get(
      keys: Seq[K]
    )(load: Seq[K] => Future[Map[K, V]],
      expiry: Long
    )(
      implicit
      ec: ExecutionContext
    ): Future[Map[K, V]] =
    for {
      cached <- cache.get(keys)
      missingKeys = keys.filterNot(cached.contains)
      loaded <- load(missingKeys)
      _ <- if (loaded.isEmpty) Future.unit
      else cache.put(loaded, expiry)
      res = cached ++ loaded
    } yield res
}
