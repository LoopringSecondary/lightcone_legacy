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

package org.loopring.lightcone.gateway.inject

import akka.actor._
import akka.cluster.Cluster
import akka.cluster.singleton._
import akka.stream.ActorMaterializer
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.google.inject._
import com.google.inject.name.Named
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.gateway.api.HttpAndIOServer
import org.loopring.lightcone.gateway.api.service._
import org.loopring.lightcone.gateway.jsonrpc._
import org.loopring.lightcone.gateway.socketio._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.collection.JavaConverters._

object CoreModule {
  def apply(config: Config) = new CoreModule(config)

  class ActorMaterializerProvider @Inject() (system: ActorSystem)
    extends Provider[ActorMaterializer] {
    override def get() = ActorMaterializer()(system)
  }
}

class CoreModule(config: Config)
  extends AbstractModule
  with ScalaModule
  with AssistedInjectFactoryModule[Binder] {
  import CoreModule._

  override def configure(): Unit = {
    val system = ActorSystem("lightcone", config)

    bind[ActorSystem].toInstance(system)
    bind[Cluster].toInstance(Cluster(system))
    bind[Config].toInstance(system.settings.config)
    bind[TokenSpendablesService].to[TokenSpendablesServiceImpl]

    bind[ActorMaterializer]
      .toProvider[ActorMaterializerProvider].asEagerSingleton()

    val session = SlickSession.forConfig(
      DatabaseConfig.forConfig[JdbcProfile](
        "slick-mysql", system.settings.config
      )
    )
    bind[SlickSession].toInstance(session)
    system.registerOnTermination(() ⇒ session.close())

    // QUESTION(Doan): 这两个参数是不是反了？
    bindFactory[ProxyActorProvider, ProxyActor]()
  }

  @Provides
  @Singleton
  @Named("cluster_proxy")
  def providerProxyMap(config: Config, system: ActorSystem): Map[String, ActorRef] = {

    def proxy(name: String, system: ActorSystem): ActorRef = {
      system.actorOf(
        ClusterSingletonProxy.props(
          singletonManagerPath = s"/user/${name}",
          settings = ClusterSingletonProxySettings(system)
        ),
        name = s"proxy_${name}"
      )
    }

    config.getStringList("akka.cluster.routees").asScala.map { path ⇒
      path → proxy(path, system)
    }.toMap
  }

  // QUESTION(Doan): proxy参数好像没用起来
  @Provides
  @Singleton
  def provideHttpAndIOServer(proxy: ProxyActor)(
    implicit
    injector: Injector,
    system: ActorSystem,
    mat: ActorMaterializer
  ): HttpAndIOServer = {
    // 这里注册需要反射类
    val settings = JsonRpcSettings().register[TokenSpendablesServiceImpl]
    val jsonRpcServer = new JsonRpcServer(settings)
    val ioServer = new SocketIOServer(jsonRpcServer, EventRegistering()
      .append("getBalance", 10000, "balance"))

    new HttpAndIOServer(jsonRpcServer, ioServer)
  }
}
