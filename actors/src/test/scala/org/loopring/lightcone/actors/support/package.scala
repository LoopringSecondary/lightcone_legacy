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
import org.loopring.lightcone.proto.XTokenMetadata
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

package object support {

  val WETH_TOKEN = XTokenMetadata(
    "0x08D24FC29CDccF8e9Ca45Eef05384c58F8a8E94F",
    18,
    0.4,
    "WETH",
    1000
  )

  val LRC_TOKEN = XTokenMetadata(
    "0xa345b6c2e5ce5970d026CeA8591DC28958fF6Edc",
    18,
    0.4,
    "LRC",
    1000
  )

  val GTO_TOKEN = XTokenMetadata(
    "0xBe6727c37cD9c5679Fa99c9A8B4E66035c0A3735",
    18,
    0.4,
    "LRC",
    1000
  )

  val RDN_TOKEN = XTokenMetadata(
    "0xD80805F6d12C9342195cAab20efFa761a4964dD4",
    18,
    0.4,
    "LRC",
    1000
  )

  val REP_TOKEN = XTokenMetadata(
    "0xd319078729D5bE3A89b79a11b4665acE9b6cF61E",
    18,
    0.4,
    "LRC",
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
