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

package org.loopring.lightcone.actors.marketcap

import akka.actor.ActorSystem
import com.typesafe.config.Config
import redis.{ RedisClient, RedisCluster, RedisCommands, RedisServer }

object CacherSettings {

  def apply(config: Config)(implicit system: ActorSystem): CacherSettings = new CacherSettings(config)

}

private[marketcap] class CacherSettings(config: Config)(implicit system: ActorSystem) {

  lazy val register: Either[RedisClient, RedisCluster] = {

    import scala.collection.JavaConverters._

    val settings = config.getObjectList("redis").asScala.map(_.toConfig).map { cfg ⇒
      val host = cfg.getString("host")
      val port = cfg.getInt("port")
      (host, port)
    }

    require(settings.nonEmpty, "can not found about redis settings")

    settings match {
      case Seq((h, p)) ⇒
        val client = RedisClient(host = h, port = p)
        Left(client)
      case Seq(_*) ⇒
        val cluster = RedisCluster(settings.map {
          case (h, p) ⇒ RedisServer(host = h, port = p)
        })
        Right(cluster)
    }

  }

  lazy val redisClient: RedisCommands =
    if (register.isLeft) register.left.get else register.right.get

}
