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

package io.lightcone.persistence

import com.dimafeng.testcontainers._
import com.typesafe.config.ConfigFactory
import io.lightcone.lib._
import io.lightcone.core._
import org.scalatest._
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

trait ServiceSpec[S]
    extends FlatSpec
    with ForAllTestContainer
    with BeforeAndAfterAll
    with Matchers {

  override val container = new MySQLContainer(
    configurationOverride = Some("db"),
    mysqlImageVersion = Some("mysql:5.7.18"),
    databaseName = Some("lightcone_test"),
    mysqlUsername = Some("test"),
    mysqlPassword = Some("test")
  )

  implicit val ec = ExecutionContext.global
  implicit var dbConfig: DatabaseConfig[JdbcProfile] = _
  implicit val timeProvider = new SystemTimeProvider()
  def getService(): S
  var service: S = _
  def createTables(): Unit

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
    createTables()
  }

  override def beforeAll = {
    println(
      s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`"
    )
  }

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }

  def generateRawOrder(
      owner: String = "0xb7e0dae0a3e4e146bcaf0fe782be5afb14041a10",
      tokenS: String = "0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1",
      tokenB: String = "0x8B75225571ff31B58F95C704E05044D5CF6B32BF",
      status: OrderStatus = OrderStatus.STATUS_NEW,
      validSince: Int,
      validUntil: Int,
      amountS: BigInt = "10".zeros(18),
      amountB: BigInt = "1".zeros(18),
      tokenFee: String = "0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1",
      amountFee: BigInt = "3".zeros(18)
    ): RawOrder = {
    val createAt = timeProvider.getTimeMillis
    val state =
      RawOrder.State(
        createdAt = createAt,
        updatedAt = createAt,
        status = status
      )
    val fee = RawOrder.FeeParams(
      tokenFee = tokenFee,
      amountFee = BigInt(111)
    )
    val since = if (validSince > 0) validSince else (createAt / 1000).toInt
    val until =
      if (validUntil > 0) validUntil else (createAt / 1000).toInt + 20000
    val param = RawOrder.Params(validUntil = until)
    val marketId = MarketHash(MarketPair(tokenS, tokenB)).longId
    val hash = Hash.sha3(
      BigInt(createAt).toByteArray ++
        Numeric.hexStringToByteArray(owner) ++
        Numeric.hexStringToByteArray(tokenS) ++
        Numeric.hexStringToByteArray(tokenB) ++
        Numeric.hexStringToByteArray(tokenFee) ++
        amountS.toByteArray ++
        amountB.toByteArray ++
        amountFee.toByteArray
    )
    RawOrder(
      owner = owner,
      hash = Numeric.toHexString(hash),
      version = 1,
      tokenS = tokenS,
      tokenB = tokenB,
      amountS = BigInt(11),
      amountB = BigInt(12),
      validSince = since,
      state = Some(state),
      feeParams = Some(fee),
      params = Some(param),
      marketId = marketId,
      marketEntityId = Math.abs(marketId.hashCode),
      accountEntityId = Math.abs(owner.hashCode % 100)
    )
  }
}
