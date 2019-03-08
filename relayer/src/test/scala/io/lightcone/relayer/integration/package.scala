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

import akka.actor.ActorSystem
import io.lightcone.relayer._
import net.codingwell.scalaguice.InjectorExtensions._
import scala.math.BigInt

package object intergration {

  println(s"### jdbcUrl ${mysqlContainer.jdbcUrl}")

  //start ActorySystem
  IntegrationStarter.starting()
  implicit val ethQueryDataProvider =
    IntegrationStarter.starter.ethQueryDataProvider
  implicit val ethAccessDataProvider =
    IntegrationStarter.starter.ethQueryDataProvider
  val entryPointActor = IntegrationStarter.starter.entrypointActor
  val eventDispatcher = IntegrationStarter.starter.eventDispatcher

  implicit val system =
    IntegrationStarter.starter.injector.instance[ActorSystem]
  implicit val ec = system.dispatcher

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }
}
