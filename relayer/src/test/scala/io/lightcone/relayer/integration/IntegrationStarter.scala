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
import akka.actor.ActorRef
import com.google.inject.{Guice, Injector}
import com.typesafe.config.ConfigFactory
import io.lightcone.relayer.actors.EntryPointActor
import io.lightcone.relayer._
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.ethereum.EventDispatcher
import io.lightcone.relayer.integration.mock.{
  EthereumAccessDataProvider,
  EthereumQueryDataProvider
}
import org.scalamock.scalatest.MockFactory
import net.codingwell.scalaguice.InjectorExtensions._

object IntegrationStarter {
  val starter = new IntegrationStarter()

  def starting() = {
    starter.starting()
  }
}

class IntegrationStarter extends MockFactory {
  implicit val ethQueryDataProvider = mock[EthereumQueryDataProvider]
  implicit val ethAccessDataProvider = mock[EthereumAccessDataProvider]

  private[integration] var injector: Injector = _
  private[integration] var entrypointActor: ActorRef = _
  private[integration] var eventDispatcher: EventDispatcher = _

  def starting(): Unit = {
    val config = ConfigFactory.load()
    injector = Guice.createInjector(new CoreModule(config, true))
    injector
      .instance[CoreDeployerForTest]
      .deploy(ethAccessDataProvider, ethQueryDataProvider)

    Thread.sleep(5000) //waiting for system ready

    eventDispatcher = injector.instance[EventDispatcher]
    entrypointActor =
      injector.instance[Lookup[ActorRef]].get(EntryPointActor.name)

  }
}
