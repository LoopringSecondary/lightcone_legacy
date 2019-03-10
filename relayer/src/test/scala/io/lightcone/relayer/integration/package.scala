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

package io.lightcone.relayer
import akka.actor.ActorSystem
import akka.util.Timeout
import io.lightcone.core._
import io.lightcone.lib.{Address, SystemTimeProvider}
import io.lightcone.relayer.implicits._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.external.FiatExchangeRateFetcher
import net.codingwell.scalaguice.InjectorExtensions._

import scala.math.BigInt
import scala.concurrent.duration._

package object integration extends MockHelper with DbHelper {

  val WETH_TOKEN = TokenMetadata(
    address = Address("0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc").toString,
    decimals = 18,
    burnRateForMarket = 0.4,
    burnRateForP2P = 0.5,
    symbol = "WETH",
    name = "WETH",
    status = TokenMetadata.Status.VALID
  )

  val LRC_TOKEN = TokenMetadata(
    address = Address("0x97241525fe425C90eBe5A41127816dcFA5954b06").toString,
    decimals = 18,
    burnRateForMarket = 0.4,
    burnRateForP2P = 0.5,
    symbol = "LRC",
    name = "LRC",
    status = TokenMetadata.Status.VALID
  )

  val GTO_TOKEN = TokenMetadata(
    address = Address("0x2D7233F72AF7a600a8EbdfA85558C047c1C8F795").toString,
    decimals = 18,
    burnRateForMarket = 0.4,
    burnRateForP2P = 0.5,
    symbol = "GTO",
    name = "GTO",
    status = TokenMetadata.Status.VALID
  )

  val LRC_WETH_MARKET = MarketMetadata(
    status = MarketMetadata.Status.ACTIVE,
    baseTokenSymbol = LRC_TOKEN.symbol,
    quoteTokenSymbol = WETH_TOKEN.symbol,
    maxNumbersOfOrders = 1000,
    priceDecimals = 6,
    orderbookAggLevels = 6,
    precisionForAmount = 5,
    precisionForTotal = 5,
    browsableInWallet = true,
    marketPair = Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)),
    marketHash =
      MarketHash(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)).toString
  )

  val GTO_WETH_MARKET = MarketMetadata(
    status = MarketMetadata.Status.ACTIVE,
    baseTokenSymbol = GTO_TOKEN.symbol,
    quoteTokenSymbol = WETH_TOKEN.symbol,
    maxNumbersOfOrders = 500,
    priceDecimals = 6,
    orderbookAggLevels = 5,
    precisionForAmount = 5,
    precisionForTotal = 5,
    browsableInWallet = true,
    marketPair = Some(
      MarketPair(baseToken = GTO_TOKEN.address, quoteToken = WETH_TOKEN.address)
    ),
    marketHash =
      MarketHash(MarketPair(GTO_TOKEN.address, WETH_TOKEN.address)).toString
  )

  val TOKENS = Seq(WETH_TOKEN, LRC_TOKEN, GTO_TOKEN)

  val TOKEN_SLUGS_SYMBOLS = Seq(
    ("ETH", "ethereum"),
    ("BTC", "bitcoin"),
    ("WETH", "weth"),
    ("LRC", "loopring"),
    ("GTO", "gifto"),
    (Currency.RMB.name, Currency.RMB.getSlug()),
    (Currency.JPY.name, Currency.JPY.getSlug()),
    (Currency.EUR.name, Currency.EUR.getSlug()),
    (Currency.GBP.name, Currency.GBP.getSlug())
  )

  val MARKETS = Seq(LRC_WETH_MARKET, GTO_WETH_MARKET)

  println(s"### jdbcUrl ${mysqlContainer.jdbcUrl}")

  var ethQueryDataProvider = mock[EthereumQueryDataProvider]
  var ethAccessDataProvider = mock[EthereumAccessDataProvider]
  setDefaultExpects()
  val starter = new IntegrationStarter()
  starter.starting()
  //start ActorySystem
  val entryPointActor = starter.entrypointActor
  val eventDispatcher = starter.eventDispatcher
  val dbModule = starter.injector.instance[DatabaseModule]

  val metadataManager = starter.injector.instance[MetadataManager]

  val fiatExchangeRateFetcher =
    starter.injector.instance[FiatExchangeRateFetcher]
  implicit val system = starter.injector.instance[ActorSystem]
  implicit val ec = system.dispatcher
  implicit val timeout: Timeout = Timeout(5.second)

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }
  implicit val timeProvider = new SystemTimeProvider()
}
