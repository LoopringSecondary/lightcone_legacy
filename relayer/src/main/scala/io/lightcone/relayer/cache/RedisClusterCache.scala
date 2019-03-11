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

package io.lightcone.relayer.cache

import io.lightcone.lib.cache._
import redis._
import scala.concurrent._

final class RedisClusterCache(
    redis: RedisCluster
  )(
    implicit
    val ex: ExecutionContext)
    extends Cache[String, Array[Byte]] {

  def get(keys: Seq[String]): Future[Map[String, Array[Byte]]] =
    Future.sequence {
      keys.map { k =>
        redis.get(k).map(v => k -> v)
      }
    }.map {
      _.filter {
        case (_, v) => v.isDefined
      }.map {
        case (k, v) => k -> v.get.toArray
      }.toMap
    }

  def del(keys: Seq[String]): Future[Unit] =
    for {
      _ <- Future.sequence(keys.map(k => redis.del(k)))
    } yield Unit

  def put(
      keyValues: Map[String, Array[Byte]],
      expiry: Long
    ): Future[Boolean] =
    for {
      _ <- Future.sequence(keyValues.map {
        case (k, v) =>
          if (expiry > 0) redis.set(k, new String(v), Some(expiry))
          else redis.set(k, new String(v))
      })
    } yield true
}
