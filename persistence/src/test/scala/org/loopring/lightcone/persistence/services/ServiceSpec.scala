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

package org.loopring.lightcone.persistence.services

import com.dimafeng.testcontainers.{ ForAllTestContainer, MySQLContainer }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import slick.basic.DatabaseConfig
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import slick.jdbc.JdbcProfile

trait ServiceSpec[S]
  extends FlatSpec
  with ForAllTestContainer
  with BeforeAndAfterAll
  with Matchers {

  override val container = new MySQLContainer(
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone_test"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )

  implicit val ec = ExecutionContext.global
  implicit var dbConfig: DatabaseConfig[JdbcProfile] = _
  def getService(): S
  var service: S = _
  def createTables(): Future[Any]

  override def afterStart(): Unit = {
    dbConfig = DatabaseConfig.forConfig[JdbcProfile](
      "",
      ConfigFactory.parseString(s"""
        profile = "slick.jdbc.MySQLProfile$$"
        db {
          url="${container.jdbcUrl}?useSSL=false"
          user="${container.username}"
          password="${container.password}"
          driver="${container.driverClassName}"
          maxThreads = 1
        }""")
    )
    service = getService()
    val result = createTables()
    Await.result(result, 5.second)
  }

  override def beforeAll = {
    println(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }
}
