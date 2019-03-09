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

package io.lightcone.persistence.cache

import org.loopring.lightcone.lib.cache._
import redis._
import scala.concurrent._

final class RedisCache(redis: RedisCluster)(implicit val ex: ExecutionContext)
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

  def get(key: String): Future[Option[Array[Byte]]] = {
    redis.get(key).map(_.map(_.toArray))
  }

  def del(key: String): Future[Unit] =
    for {
      _ <- redis.del(key)
    } yield Unit

  def put(
      key: String,
      value: Array[Byte]
    ): Future[Boolean] = {
    redis.set(key, new String(value))
  }
}
