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

import com.dimafeng.testcontainers.MySQLContainer
import com.typesafe.config.ConfigFactory
import org.junit.runner.Description
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.XTokenMeta
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

package object support {

  val WETH_TOKEN = XTokenMeta(
    Address("0x8B75225571ff31B58F95C704E05044D5CF6B32BF").toString,
    18,
    0.4,
    "WETH",
    1000
  )

  val LRC_TOKEN = XTokenMeta(
    Address("0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1").toString,
    18,
    0.4,
    "LRC",
    1000
  )

  val GTO_TOKEN = XTokenMeta(
    Address("0x17839E1AC3B46F12f74465BFbc754aB487B093AB").toString,
    18,
    0.4,
    "GTO",
    1000
  )

  val RDN_TOKEN = XTokenMeta(
    Address("0xcF30e28DD8570e8d5B769CEcd293Bdc0E28bF0d2").toString,
    18,
    0.4,
    "RDN",
    1000
  )

  val REP_TOKEN = XTokenMeta(
    Address("0xf386CedfAA2d1071e52C81554D4200c0aD0aDC24").toString,
    18,
    0.4,
    "REP",
    1000
  )

  val container = new MySQLContainer(
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone_test"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )

  implicit private val suiteDescription =
    Description.createSuiteDescription(this.getClass)

  container.starting()

  Thread.sleep(10000)

  val dbConfig1: DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile](
      "",
      ConfigFactory.parseString(s"""
        profile = "slick.jdbc.MySQLProfile$$"
        db {
          url="${container.jdbcUrl}?useSSL=false"
          user="${container.username}"
          password="${container.password}"
          driver="${container.driverClassName}"
          maxThreads = 4
        }""")
    )
}
