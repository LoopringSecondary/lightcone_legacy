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

package org.loopring.lightcone.actors.support

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.dimafeng.testcontainers.{ForAllTestContainer, MySQLContainer}
import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.base.MapBasedLookup
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.market.{
  RingIncomeEstimator,
  RingIncomeEstimatorImpl
}
import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.proto.XTokenMeta
import org.scalatest._
import org.slf4s.Logging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.math.BigInt
//启动system、以及必须的元素，包括system，TokenMetaData，等
abstract class CommonSpec(configStr: String)
    extends TestKit(
      ActorSystem(
        "Lightcone",
        ConfigFactory
          .parseString(configStr)
          .withFallback(ConfigFactory.load())
      )
    )
    with ImplicitSender
    with Matchers
    with WordSpecLike
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Logging {

  override def afterAll: Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, 10.seconds, false)
  }

  //akka
  implicit val timeProvider = new SystemTimeProvider()
  implicit val timeout = Timeout(5 second)
  implicit val ec = system.dispatcher
  implicit val config = system.settings.config
  implicit val materializer = ActorMaterializer()(system)

  //  log.info(s"init config: ${config}")

  //token info
  implicit val tokenManager = new TokenManager()

  tokenManager.addToken(WETH_TOKEN)
  tokenManager.addToken(LRC_TOKEN)
  implicit val tokenValueEstimator = new TokenValueEstimator()
  implicit val dustOrderEvaluator = new DustOrderEvaluator()

  //relay
  implicit val actors = new MapBasedLookup[ActorRef]()
  implicit val ringIncomeEstimator: RingIncomeEstimator =
    new RingIncomeEstimatorImpl()

  //actors
  //  val refresher = system.actorOf(
  //    Props(new TokenMetadataRefresher),
  //    "token_metadata_refresher"
  //  )

  //  val listener =
  //    system.actorOf(Props[BadMessageListener], "bad_message_listener")
  //  system.eventStream.subscribe(listener, classOf[UnhandledMessage])
  //  system.eventStream.subscribe(listener, classOf[DeadLetter])

  Thread.sleep(5000) //暂停5s，等待集群准备完毕

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }
}
