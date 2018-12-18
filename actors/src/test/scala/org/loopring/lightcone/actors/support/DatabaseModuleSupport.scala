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

import org.loopring.lightcone.actors.core.DatabaseQueryActor
import org.loopring.lightcone.persistence.DatabaseModule
import org.scalatest.BeforeAndAfterAll

trait DatabaseModuleSupport extends BeforeAndAfterAll {
  my: CommonSpec =>

  implicit val dbConfig = dbConfig1
  implicit val dbModule = new DatabaseModule()
  dbModule.dropTables()
  dbModule.createTables()
  actors.add(DatabaseQueryActor.name, DatabaseQueryActor.startShardRegion)

//  override val container = new MySQLContainer(
//    mysqlImageVersion = Some("mysql:5.7.18"),
//    databaseName = Some("lightcone_test"),
//    mysqlUsername = Some("test"),
//    mysqlPassword = Some("test")
//  )
//
//  implicit var dbModule: DatabaseModule = _
//  override def afterStart(): Unit = {
//    implicit val dbConfig: DatabaseConfig[JdbcProfile] =
//      DatabaseConfig.forConfig[JdbcProfile](
//        "",
//        ConfigFactory.parseString(s"""
//        profile = "slick.jdbc.MySQLProfile$$"
//        db {
//          url="${container.jdbcUrl}?useSSL=false"
//          user="${container.username}"
//          password="${container.password}"
//          driver="${container.driverClassName}"
//          maxThreads = 4
//        }""")
//      )
//    dbModule = new DatabaseModule()
//    dbModule.createTables()
//    actors.add(DatabaseQueryActor.name, DatabaseQueryActor.startShardRegion)
//  }

}
