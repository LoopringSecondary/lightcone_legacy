/*

  Copyright 2017 Loopring Project Ltd (Loopring Foundation).

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.

*/

package org.loopring.lightcone.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.cluster.Cluster
import com.google.inject.AbstractModule
import com.typesafe.config.Config
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.actors.base.{Lookup, MapBasedLookup}
import org.loopring.lightcone.core.base.TokenMetadataManager
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class CoreModule(config: Config)
  extends AbstractModule with ScalaModule {
  override def configure(): Unit = {
    implicit val system = ActorSystem("Lightcone", config)
    implicit val cluster = Cluster(system)

    bind[Config].toInstance(config)
    bind[DatabaseConfig[JdbcProfile]]
      .toInstance(DatabaseConfig.forConfig("db.default", config))

    //同时需要启动actor并开始同步
    implicit val tmm = new TokenMetadataManager()
    bind[TokenMetadataManager].toInstance(tmm)

    val actors = new MapBasedLookup[ActorRef]()
    bind[Lookup[ActorRef]].toInstance(actors)


    //role: core-account(路由以及启动), core-market, core-orderbook, core-settlement
    //role: ethereum
    //persistence:

  }
}
