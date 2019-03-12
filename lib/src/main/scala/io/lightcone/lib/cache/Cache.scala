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

trait Cache[K, V] {

  implicit val ec: ExecutionContext
  def get(keys: Seq[K]): Future[Map[K, V]]
  def del(keys: Seq[K]): Future[Unit]

  def put(
      keyValues: Map[K, V],
      expiry: Long
    ): Future[Boolean]

  def get(key: K): Future[Option[V]] = get(Seq(key)).map(_.get(key))
  def del(key: K): Future[Unit] = del(Seq(key))

  def put(
      key: K,
      value: V,
      expiry: Long
    ): Future[Boolean] = put(Map(key -> value), expiry)

  // advanced methods
  def read(
      key: K
    )(load: K => Future[Option[V]],
      expiry: Long
    ): Future[Option[V]] = {

    def _load(keys: Seq[K]): Future[Map[K, V]] =
      for {
        loaded <- load(keys(0))
        map_ = {
          if (loaded.isEmpty) Map.empty[K, V]
          else Map(keys(0) -> loaded.get)
        }
      } yield map_

    for {
      map_ <- read(Seq(key))(_load, expiry)
      res = map_.get(key)
    } yield res
  }

  def read(
      keys: Seq[K]
    )(load: Seq[K] => Future[Map[K, V]],
      expiry: Long
    ): Future[Map[K, V]] =
    for {
      cached <- get(keys)
      missingKeys = keys.filterNot(cached.contains)
      loaded <- load(missingKeys)
      _ <- if (loaded.isEmpty) Future.unit
      else put(loaded, expiry)
      res = cached ++ loaded
    } yield res
}
