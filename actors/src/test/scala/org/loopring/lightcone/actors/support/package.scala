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

import com.dimafeng.testcontainers.{GenericContainer, MySQLContainer}
import com.typesafe.config.ConfigFactory
import org.junit.runner.Description
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.TokenMeta
import org.testcontainers.containers.wait.strategy.Wait
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

package object support {

  val WETH_TOKEN = TokenMeta(
    Address("0x8B75225571ff31B58F95C704E05044D5CF6B32BF").toString,
    18,
    0.4,
    "WETH",
    1000
  )

  val LRC_TOKEN = TokenMeta(
    Address("0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1").toString,
    18,
    0.4,
    "LRC",
    1000
  )

  val GTO_TOKEN = TokenMeta(
    Address("0x17839E1AC3B46F12f74465BFbc754aB487B093AB").toString,
    18,
    0.4,
    "GTO",
    1000
  )

  val RDN_TOKEN = TokenMeta(
    Address("0xcF30e28DD8570e8d5B769CEcd293Bdc0E28bF0d2").toString,
    18,
    0.4,
    "RDN",
    1000
  )

  val REP_TOKEN = TokenMeta(
    Address("0xf386CedfAA2d1071e52C81554D4200c0aD0aDC24").toString,
    18,
    0.4,
    "REP",
    1000
  )

  // TODO(hongyu): All code below should be moved to other places, such as EthereumSupport.scala
  // and DatabaseModuleSupport.scala.

  implicit private val suiteDescription =
    Description.createSuiteDescription(this.getClass)

  val mysqlContainer = new MySQLContainer(
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone_test"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )

  mysqlContainer.starting()

  val ethContainer = GenericContainer(
    "trufflesuite/ganache-cli:latest",
    exposedPorts = Seq(8545),
    waitStrategy = Wait.forListeningPort()
  )

  ethContainer.starting()

  // Thread.sleep(2000)

  val dbConfig1: DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile](
      "",
      ConfigFactory.parseString(s"""
        profile = "slick.jdbc.MySQLProfile$$"
        db {
          url="${mysqlContainer.jdbcUrl}?useSSL=false"
          user="${mysqlContainer.username}"
          password="${mysqlContainer.password}"
          driver="${mysqlContainer.driverClassName}"
          maxThreads = 4
        }""")
    )

  val ethConfigStr = s"""ethereum_client_monitor {
                        |    pool-size = 1
                        |    check-interval-seconds = 10
                        |    healthy-threshold = 0.2
                        |    nodes = [
                        |        {
                        |        host = "${ethContainer.containerIpAddress}"
                        |        port = ${ethContainer.mappedPort(8545)}
                        |        }
                        |    ]
                        |}""".stripMargin

  val transactionReCordConfigStr = s"""
     transaction-record_0 {
        db {
           profile = "slick.jdbc.MySQLProfile$$"
           db {
             url="${mysqlContainer.jdbcUrl}?useSSL=false"
             user="${mysqlContainer.username}"
             password="${mysqlContainer.password}"
             driver="${mysqlContainer.driverClassName}"
             maxThreads = 4
           }
        }
     }
     transaction-record_1 {
        db {
           profile = "slick.jdbc.MySQLProfile$$"
           db {
             url="${mysqlContainer.jdbcUrl}?useSSL=false"
             user="${mysqlContainer.username}"
             password="${mysqlContainer.password}"
             driver="${mysqlContainer.driverClassName}"
             maxThreads = 4
           }
        }
     }
    """.stripMargin

  println(s"""
    host = ${ethContainer.containerIpAddress}
    port = ${ethContainer.mappedPort(8545)}
    """)

  Thread.sleep(2000)
}
