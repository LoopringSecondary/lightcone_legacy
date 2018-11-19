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

package org.loopring.lightcone.auxiliary

import akka.actor.ActorSystem
import akka.stream.alpakka.slick.scaladsl.SlickSession
import com.google.inject.{ AbstractModule, Provides, Singleton }
import net.codingwell.scalaguice.ScalaModule
import org.loopring.lightcone.auxiliary.service._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class CoreModule extends AbstractModule with ScalaModule {

  override def configure(): Unit = {
    // TODO(xiaolu): enable Redis-cache for these services
    bind[ExchangeTickerService].to[ExchangeTickerServiceImpl]
    bind[TokenIcoInfoService].to[TokenIcoInfoServiceImpl]
    bind[TokenInfoService].to[TokenInfoServiceImpl]
    bind[TokenTickerInfoService].to[TokenTickerInfoServiceImpl]
  }

  @Provides
  @Singleton
  def provideSlickSession(system: ActorSystem): SlickSession = {
    // for db session
    val databaseConfig = DatabaseConfig.forConfig[JdbcProfile](
      "db.default",
      system.settings.config
    )
    val session = SlickSession.forConfig(databaseConfig)
    system.registerOnTermination(() ⇒ session.close())
    session
  }

}
