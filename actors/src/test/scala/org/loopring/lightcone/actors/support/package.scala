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

import java.util.concurrent.atomic.AtomicInteger
import com.dimafeng.testcontainers.{GenericContainer, MySQLContainer}
import com.typesafe.config.ConfigFactory
import org.junit.runner.Description
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto.{MarketId, MarketMetadata, TokenMetadata}
import org.testcontainers.containers.wait.strategy.Wait
import org.web3j.crypto.Credentials
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

package object support {

  val WETH_TOKEN = TokenMetadata(
    address = Address("0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "WETH",
    name = "WETH",
    usdPrice = 1000,
    status = TokenMetadata.Status.ENABLED
  )

  val LRC_TOKEN = TokenMetadata(
    address = Address("0x97241525fe425C90eBe5A41127816dcFA5954b06").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "LRC",
    name = "LRC",
    usdPrice = 1000,
    status = TokenMetadata.Status.ENABLED
  )

  val GTO_TOKEN = TokenMetadata(
    address = Address("0x2D7233F72AF7a600a8EbdfA85558C047c1C8F795").toString,
    decimals = 18,
    burnRate = 0.4,
    symbol = "GTO",
    name = "GTO",
    usdPrice = 1000,
    status = TokenMetadata.Status.ENABLED
  )

  val LRC_WETH_MARKET = MarketMetadata(
    status = MarketMetadata.Status.ENABLED,
    secondaryTokenSymbol = LRC_TOKEN.symbol,
    primaryTokenSymbol = WETH_TOKEN.symbol,
    maxNumbersOfOrders = 1000,
    priceDecimals = 6,
    orderbookAggLevels = 6,
    precisionForAmount = 5,
    precisionForTotal = 5,
    browsableInWallet = true,
    marketId = Some(
      MarketId(primary = WETH_TOKEN.address, secondary = LRC_TOKEN.address)
    ),
    marketHash =
      MarketHashProvider.convert2Hex(LRC_TOKEN.address, WETH_TOKEN.address)
  )

  val GTO_WETH_MARKET = MarketMetadata(
    status = MarketMetadata.Status.ENABLED,
    secondaryTokenSymbol = GTO_TOKEN.symbol,
    primaryTokenSymbol = WETH_TOKEN.symbol,
    maxNumbersOfOrders = 500,
    priceDecimals = 6,
    orderbookAggLevels = 5,
    precisionForAmount = 5,
    precisionForTotal = 5,
    browsableInWallet = true,
    marketId = Some(
      MarketId(primary = WETH_TOKEN.address, secondary = GTO_TOKEN.address)
    ),
    marketHash =
      MarketHashProvider.convert2Hex(GTO_TOKEN.address, WETH_TOKEN.address)
  )

  val TOKENS = Seq(
    WETH_TOKEN,
    LRC_TOKEN,
    GTO_TOKEN
  )

  val MARKETS = Seq(
    LRC_WETH_MARKET,
    GTO_WETH_MARKET
  )

  //第一个地址为特殊地址，eth以及erc20金额和授权，都足够大
  val accounts = Seq(
    "0x7c71142c72a019568cf848ac7b805d21f2e0fd8bc341e8314580de11c6a397bf",
    "0x4c5496d2745fe9cc2e0aa3e1aad2b66cc792a716decf707ddb3f92bd2d93ad24",
    "0x04b9e9d7c1385c581bab12600834f4f90c6e19142faae6c2de670bfb4b5a08c4",
    "0xa99a8d27d06380565d1cf6c71974e7707a81676c4e7cb3dad2c43babbdca2d23",
    "0x9fda7156489be5244d8edc3b2dafa6976c14c729d54c21fb6fd193fb72c4de0d",
    "0x2949899bb4312754e11537e1e2eba03c0298608effeab21620e02a3ef68ea58a",
    "0x86768554c0bdef3a377d2dd180249936db7010a097d472293ae7808536ea45a9",
    "0x6be54ed053274a3cda0f03aa9f9ddd4cafbb7bd03ceffe8731ed76c0f0be3297",
    "0x05a94ee2777a19a7e1ed0c58d2d61b857bb9cd712168cd16848163f12eb80e45",
    "0x324b720be128e8cacb16395deac8b1332d02da4b2577d4cd94cc453302320ea7"
  ).map(Credentials.create)

  implicit private val suiteDescription =
    Description.createSuiteDescription(this.getClass)

  val mysqlContainer = new MySQLContainer(
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone_test"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )
  mysqlContainer.starting()

  //todo:暂时未生效
  //  try Unreliables.retryUntilTrue(
  //    10,
  //    TimeUnit.SECONDS,
  //    () => {
  //      mysqlContainer.mappedPort(3306) > 0
  //    }
  //  )
  //  catch {
  //    case e: TimeoutException =>
  //      throw new ContainerLaunchException(
  //        "Timed out waiting for container port to open mysqlContainer should be listening)"
  //      )
  //  }

  Thread.sleep(2000)

  val ethContainer = GenericContainer(
    "kongliangzhong/loopring-ganache:v2",
    exposedPorts = Seq(8545),
    waitStrategy = Wait.forListeningPort()
  )

  ethContainer.starting()

  val mysqlConfigStr = s"""
        profile = "slick.jdbc.MySQLProfile$$"
        db {
          url="${mysqlContainer.jdbcUrl}?useSSL=false"
          user="${mysqlContainer.username}"
          password="${mysqlContainer.password}"
          driver="${mysqlContainer.driverClassName}"
          maxThreads = 4
        }"""

  val dbConfig1: DatabaseConfig[JdbcProfile] =
    DatabaseConfig
      .forConfig[JdbcProfile]("", ConfigFactory.parseString(mysqlConfigStr))

  val transactionRecordConfigStr = s"""
     db.transaction-record.shard_0 {
         $mysqlConfigStr
     }
     db.transaction-record.shard_1 {
         $mysqlConfigStr
     }
    """.stripMargin

  val ethNodesConfigStr = s"""|nodes:[
                              | {
                              |  host = "${ethContainer.containerIpAddress}"
                              |  port = ${ethContainer.mappedPort(8545)}
                              | }
                              |]""".stripMargin

  //便于生成全局唯一的地址
  val addressGenerator = new AtomicInteger(100000)

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

}
