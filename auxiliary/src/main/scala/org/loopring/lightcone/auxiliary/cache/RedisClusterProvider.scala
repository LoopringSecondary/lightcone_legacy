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

import collection.JavaConverters._
import akka.actor.ActorSystem
import com.typesafe.config.Config
import redis._
import com.google.inject._

class RedisClusterProvider @Inject() (
    config: Config
)(
    implicit
    system: ActorSystem
) extends Provider[RedisCluster] {
  def get(): RedisCluster = {

    val servers = config.getObjectList("redis.servers").asScala
      .map { item ⇒
        val c = item.toConfig
        val host = c.getString("host")
        val port = c.getInt("port")
        val password = Some(c.getString("redis.password"))
        RedisServer(host, port, password)
      }

    RedisCluster(servers)
  }
}

