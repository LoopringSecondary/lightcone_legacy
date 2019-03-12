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

package io.lightcone.relayer
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import io.lightcone.core.MetadataManager
import io.lightcone.lib.SystemTimeProvider
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.actors._
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.ethereum.EventDispatcher
import io.lightcone.relayer.integration.starter.IntegrationStarter
import net.codingwell.scalaguice.InjectorExtensions._
import org.scalamock.scalatest.MockFactory

import scala.concurrent.duration._
import scala.math.BigInt

package object integration extends MockFactory {

  println(s"### jdbcUrl ${mysqlContainer.jdbcUrl}")

  implicit val timeout = Timeout(5 second)
  implicit val timeProvider = new SystemTimeProvider()

  val integrationStarter = new IntegrationStarter()
  integrationStarter.starting()

  val injector = integrationStarter.injector

  val eventDispatcher = injector.instance[EventDispatcher]
  val dbModule = injector.instance[DatabaseModule]
  val metadataManager = injector.instance[MetadataManager]

  //  //start ActorySystem
  val entryPointActor =
    injector.instance[Lookup[ActorRef]].get(EntryPointActor.name)

  implicit val system = injector.instance[ActorSystem]

  implicit val ec = system.dispatcher

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }
}
