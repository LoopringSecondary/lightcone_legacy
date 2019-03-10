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

package io.lightcone.relayer.integration

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.actor._
import com.google.inject.Guice
import com.google.inject.Injector
import com.typesafe.config.ConfigFactory
import io.lightcone.relayer._
import io.lightcone.relayer.actors.EntryPointActor
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.ethereum.EventDispatcher
import net.codingwell.scalaguice.InjectorExtensions._
import org.scalamock.scalatest.MockFactory

import scala.util.Try
import org.scalatest._
import org.slf4s.Logging

object IntegrationTest {
  var databaseIndex = 0L
  // import testing.Constants._
}

// Please make sure in `mysql.conf` all database dals use the same database configuration.
class IntegrationTest
    extends FlatSpec
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with Matchers
    with Logging
    with IntegrationTestHelper
    with MockFactory {

  import IntegrationTest._

  implicit val ethQueryDataProvider = mock[EthereumQueryDataProvider]
  implicit val ethAccessDataProvider = mock[EthereumAccessDataProvider]

  private var injector: Injector = _
  private var entrypointActor: ActorRef = _
  var eventDispatcher: EventDispatcher = _

  def entrypoint() = entrypointActor

  override def beforeEach() = {}

  override def afterEach(): Unit = {}

  override def beforeAll(): Unit = {
    val params = "characterEncoding=UTF-8&useSSL=false"
    val databaseUrls = s"""
    db.default.db.url:"jdbc:mysql://127.0.0.1:3306/lightcone_${databaseIndex}?${params}"
    db.postgreDefault.db.url:"jdbc:postgresql://127.0.0.1:5432/lightcone_${databaseIndex}"
    """

    val config = ConfigFactory
      .parseString(databaseUrls)
      .withFallback(ConfigFactory.load())

    injector = Guice.createInjector(new CoreModule(config, true))

    injector
      .instance[CoreDeployerForTest]
      .deploy()

    Thread.sleep(5000) //waiting for system ready

    eventDispatcher = injector.instance[EventDispatcher]
    entrypointActor =
      injector.instance[Lookup[ActorRef]].get(EntryPointActor.name)

    log.info("akka system started, actors deployed --->")
    log.info(s"database url: ${databaseUrls}")

  }

  override def afterAll(): Unit = {
    Try(Await.result(injector.instance[ActorSystem].terminate(), 10.seconds))
    Try(injector.instance[DatabaseConfigManager].close())
    log.info("<--- akka system shut down, database closed")

    // TODO(dongw): drop the current mysql and postgre databases!

    databaseIndex += 1
  }
}
