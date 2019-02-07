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

package io.lightcone.persistence.dals

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigFactory
import io.lightcone.persistence.base.BaseDal
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent.ExecutionContext

trait DalPostgreSpec[D <: BaseDal[_, _]]
    extends FlatSpec
    with ForAllTestContainer
    with BeforeAndAfterAll
    with Matchers {
  implicit val ec = ExecutionContext.global

  override val container = PostgreSQLContainer("timescale/timescaledb:latest")

  Class.forName(container.driverClassName)

  implicit var dbConfig: DatabaseConfig[JdbcProfile] = _
  def getDal(): D
  var dal: D = _

  override def afterStart(): Unit = {
    dbConfig = DatabaseConfig.forConfig[JdbcProfile](
      "",
      ConfigFactory.parseString(s"""
        profile = "slick.jdbc.PostgresProfile$$"
        db {
          url="${container.jdbcUrl}"
          user="${container.username}"
          password="${container.password}"
          driver="${container.driverClassName}"
          maxThreads = 1
        }""")
    )
    dal = getDal()
    dal.createTable()
    Thread.sleep(500)
  }

  override def beforeAll = {
    println(
      s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`"
    )
  }
}
