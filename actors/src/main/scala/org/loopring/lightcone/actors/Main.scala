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

import java.net.NetworkInterface

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{ ClusterDomainEvent, MemberUp }
import akka.cluster.pubsub.DistributedPubSubMediator._
import akka.cluster.pubsub._
import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import org.slf4s.Logging

import scala.collection.JavaConverters._
import scala.concurrent.duration._

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

  val injector = Guice.createInjector(new CoreModule(config))
  // implicit val system = ActorSystem("Lightcone", config)
  // implicit val ec = system.dispatcher
  // implicit val cluster = Cluster(system)

  // val a = system.actorOf(Props(classOf[MyActor]))

}

// TODO: remove this after docker compose works
class MyActor extends Actor with ActorLogging {
  import context.dispatcher
  override def preStart = Cluster(context.system).subscribe(self, classOf[ClusterDomainEvent])
  val mediator = DistributedPubSub(context.system).mediator
  context.system.scheduler.schedule(0 seconds, 10 seconds, self, "TICK")

  mediator ! Subscribe("topic", self)
  var i = 0L

  val nifs = NetworkInterface.getNetworkInterfaces.asScala.toSeq

  log.info(s"this node, ${context.system.settings.config.getString("clustering.hostname")}, ${nifs(0).getInetAddresses}, ${nifs(0).getInterfaceAddresses}")
  def receive = {
    case MemberUp(member) ⇒ log.info("memberUp={}", member.address)
    case "TICK" ⇒
      log.info("receive TICK")
      mediator ! Publish("topic", "event-sender:" + nifs(0).getInterfaceAddresses + "-" + i)
      i += 1
    case x ⇒
      log.info("===> receiver:" + nifs(0).getInterfaceAddresses + " msg:" + x)
  }
}
