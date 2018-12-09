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

package org.loopring.lightcone.actors

import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster.pubsub._
import akka.cluster.sharding._
import scala.concurrent.duration._
import DistributedPubSubMediator._
import akka.cluster.Cluster
import org.slf4s.Logging

object Main extends App with Logging {
  val config = ConfigFactory.load()

  val configItems = Seq(
    "akka.remote.artery.canonical.hostname",
    "akka.remote.artery.canonical.port",
    "akka.remote.bind.hostname",
    "akka.remote.bind.port"
  )

  configItems foreach { i ⇒
    log.debug(s"--> $i = ${config.getString(i)}")
  }

  // val injector = Guice.createInjector(new CoreModule(config))
  implicit val system = ActorSystem("Lightcone", config)
  implicit val ec = system.dispatcher
  implicit val cluster = Cluster(system)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case "a" ⇒ ("entity_a", "a")
    case _   ⇒ ("entity_b", "b")
  }

  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case "a" ⇒ "1111"
    case "b" ⇒ "2222"
  }

  val counterRegion: ActorRef = ClusterSharding(system).start(
    typeName = "Counter",
    entityProps = Props[MyActor],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  counterRegion ! "a"
  counterRegion ! "b"
}

// TODO: remove this after docker compose works
class MyActor extends Actor with ActorLogging {
  println("=====》 " + self.path.name)
  import context.dispatcher
  val mediator = DistributedPubSub(context.system).mediator
  context.system.scheduler.schedule(0 seconds, 10 seconds, self, "TICK")

  mediator ! Subscribe("topic", self)
  var i = 0L

  def receive = {
    case "TICK" ⇒
      log.error("hello")
      mediator ! Publish("topic", "event-" + i)
      i += 1

    case x ⇒
      log.error("===> " + x)
  }
}
