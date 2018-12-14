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

package org.loopring.lightcone.actors.core

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.cluster.sharding._
import akka.util.Timeout
import org.scalatest._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import akka.pattern._
import com.typesafe.config.ConfigFactory

class ShardSpec extends FlatSpec with Matchers {

  class ShardTestActor(
    )(
      implicit
      val timeout: Timeout)
      extends Actor
      with ActorLogging {

    def receive: Receive = {
      case x ⇒
        log.info(s"receive wrong msg: $x, ${sender()}")
        sender() ! x
    }

  }

  "shard" should "success" in {
    val config = ConfigFactory.load()
//    println(s"config: ${config}")
    implicit val system = ActorSystem("Lightcone", config)
    implicit val timeout = Timeout(1 seconds)
    implicit val ec = system.dispatcher
    val extractEntityId: ShardRegion.ExtractEntityId = {
      case s: String ⇒
        println("0xdddddd")
        (s, s)
    }

    val extractShardId: ShardRegion.ExtractShardId = {
      case s: String ⇒
        println(s"string ${s}")
        s
    }
    val multiAccountManagerActor = ClusterSharding(system).start(
      typeName = "ShardTestActor",
      entityProps = Props(new ShardTestActor()),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
//    val multiAccountManagerActor = system.actorOf(Props(new ShardTestActor()))

    println(s"aa, ${multiAccountManagerActor}")
    val f = multiAccountManagerActor ? "ffff"
    val r = Await.result(f, timeout.duration)
    info("dadddd res:" + r)
    //    f.map { s =>
//      info("ccccc:" + s)
//      assert(s == "ddd")
//    }
    Thread.sleep(1000)
    assert(true)
  }

}
