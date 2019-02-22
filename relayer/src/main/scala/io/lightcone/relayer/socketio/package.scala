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

package object socketio {

  case class Market(
      baseToken: String,
      quoteToken: String)

  case class TokenBalanceAndAllowance(
      address: String,
      balance: String,
      allowance: String,
      availableBalance: String,
      availableAllowance: String)

  case class Transaction(
      gasUsed: String = "0x0",
      hash: String,
      blockNum: String,
      time: String,
      status: String)

  case class Order(
      hash: String,
      status: String,
      dealtAmountS: String,
      dealtAmountB: String,
      dealtAmountFee: String)

  case class Ticker(
      high: Double,
      low: Double,
      last: Double,
      vol: Double,
      amount: Double,
      buy: Double,
      sell: Double,
      change: Double)
  case class TickerResponse(
      market: Market,
      ticker: Ticker)

  case class OrderBookItem(
      amount: Double,
      price: Double,
      total: Double)
  case class OrderBook(
      lastPrice: Double,
      buys: Seq[OrderBookItem],
      sells: Seq[OrderBookItem])

  case class OrderBookResponse(
      market: Market,
      level: Int,
      orderBook: OrderBook)

  case class TokenMetadata(
      status: String,
      address: String,
      website_url: String,
      precision: Int,
      burn_rate_for_market: Double,
      burn_rate_for_p2p: Double,
      usd_price: Double,
      updated_at: Long)

}
