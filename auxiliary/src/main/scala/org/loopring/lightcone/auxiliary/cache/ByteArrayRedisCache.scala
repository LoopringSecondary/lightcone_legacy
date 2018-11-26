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

package org.loopring.lightcone.core.cache

import org.loopring.lightcone.lib.cache._
import akka.actor.ActorSystem
import com.typesafe.config.Config
import redis._
import com.google.inject._

import scala.concurrent._
import akka.util.ByteString
import com.google.inject.name.Named

final class ByteArrayRedisCache @Inject() (
    redis: RedisCluster
)(
    implicit
    @Named("system-dispatcher") val ex: ExecutionContext
)
  extends ByteArrayCache {

  def get(keys: Seq[Array[Byte]]): Future[Map[Array[Byte], Array[Byte]]] = {

    Future.sequence(keys.map {
      k ⇒ redis.get(new String(k)).map(v ⇒ k -> v)
    }).map {
      _.filter {
        case (_, v) ⇒ v.isDefined
      }.map {
        case (k, v) ⇒ k -> v.get.toArray
      }.toMap
    }

  }
  def get(key: Array[Byte]): Future[Option[Array[Byte]]] = {
    redis.get(new String(key)).map(_.map(_.toArray))
  }
  def put(key: Array[Byte], value: Array[Byte]): Future[Boolean] = {
    redis.set(new String(key), new String(value))
  }
}
