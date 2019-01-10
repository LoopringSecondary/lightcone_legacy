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

import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class DatabaseConfigManager(config: Config) {
  private var nameMap = Map.empty[String, Config]
  private var confMap = Map.empty[Config, DatabaseConfig[JdbcProfile]]

  def getDatabaseConfig(name: String): DatabaseConfig[JdbcProfile] =
    nameMap.get(name) match {
      case Some(conf) => getDatabaseConfig(conf)
      case None =>
        val conf = config.getConfig(name)
        nameMap += name -> conf
        getDatabaseConfig(conf)
    }

  def getDatabaseConfig(conf: Config): DatabaseConfig[JdbcProfile] =
    confMap.get(conf) match {
      case Some(databaseConfig) => databaseConfig
      case None =>
        val databaseConfig: DatabaseConfig[JdbcProfile] =
          DatabaseConfig.forConfig("", conf)
        confMap += conf -> databaseConfig
        databaseConfig
    }
}
