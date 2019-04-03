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

package io.lightcone
import java.util.concurrent.atomic.AtomicInteger

import com.dimafeng.testcontainers.{MySQLContainer, PostgreSQLContainer}
import org.junit.runner.Description
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric

package object relayer {
  //便于生成全局唯一的地址
  private val addressGenerator = new AtomicInteger(100000)
  private val blockNumber = new AtomicInteger(100000)

  implicit private val suiteDescription =
    Description.createSuiteDescription(this.getClass)

  val mysqlContainer = new MySQLContainer(
    configurationOverride = Some("db"),
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )
  mysqlContainer.container.setPortBindings(
    java.util.Arrays.asList("3306:3306")
  )
  mysqlContainer.starting()

  val postgreContainer = PostgreSQLContainer("timescale/timescaledb:latest")
  postgreContainer.container.setPortBindings(
    java.util.Arrays.asList("5432:5432")
  )
  postgreContainer.starting()

  Thread.sleep(5000)

  def getUniqueAccount() = {
    Credentials.create(
      Numeric.toHexStringWithPrefix(
        BigInt(addressGenerator.getAndIncrement()).bigInteger
      )
    )
  }

  def getUniqueInt() = {
    addressGenerator.getAndIncrement()
  }

  def getNextBlockNumber() = {
    blockNumber.getAndIncrement()
  }

  println(s"##### ${postgreContainer.jdbcUrl}")

}
