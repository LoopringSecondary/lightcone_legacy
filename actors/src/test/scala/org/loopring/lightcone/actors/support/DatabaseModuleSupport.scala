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
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.persistence._
import org.loopring.lightcone.lib._
import org.scalatest.BeforeAndAfterAll
import org.junit.runner.Description
import org.testcontainers.containers.wait.strategy.Wait
import com.dimafeng.testcontainers.{GenericContainer, MySQLContainer}
import com.typesafe.config.ConfigFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

trait DatabaseModuleSupport extends BeforeAndAfterAll {
  my: CommonSpec =>

  implicit val dbConfig = dbConfig1

  implicit val tokenMetadataDal = new TokenMetadataDalImpl
  implicit val orderDal = new OrderDalImpl
  implicit val tradeDal = new TradeDalImpl
  implicit val tokenBalanceDal = new TokenBalanceDalImpl
  implicit val blockDal = new BlockDalImpl
  implicit val settlementTxDal = new SettlementTxDalImpl
  implicit val orderStatusMonitorDal =
    new OrderStatusMonitorDalImpl
  implicit val orderService = new OrderServiceImpl
  implicit val orderStatusMonitorService =
    new OrderStatusMonitorServiceImpl
  implicit val tokenMetadataService =
    new TokenMetadataServiceImpl
  implicit val tradeService = new TradeServiceImpl
  implicit val settlementTxService =
    new SettlementTxServiceImpl
  implicit val blockService = new BlockServiceImpl

  implicit val dbModule = new DatabaseModule(
    tokenMetadataDal,
    orderDal,
    tradeDal,
    tokenBalanceDal,
    blockDal,
    settlementTxDal,
    orderStatusMonitorDal,
    orderService,
    orderStatusMonitorService,
    tokenMetadataService,
    tradeService,
    blockService,
    settlementTxService
  )

  dbModule.dropTables()
  dbModule.createTables()
  actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)

  //  override val mySqlContainer = new MySQLContainer(
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
  //          url="${mySqlContainer.jdbcUrl}?useSSL=false"
  //          user="${mySqlContainer.username}"
  //          password="${mySqlContainer.password}"
  //          driver="${mySqlContainer.driverClassName}"
  //          maxThreads = 4
  //        }""")
  //      )
  //    dbModule = new DatabaseModule()
  //    dbModule.createTables()
  //    actors.add(DatabaseQueryActor.name, DatabaseQueryActor.start)
  //  }

}
