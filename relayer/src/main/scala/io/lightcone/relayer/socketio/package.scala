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

  case class SubscribeBalanceAndAllowance(
      addresses: Seq[String],
      tokens: Seq[String])

  case class SubscribeTransaction(
      addresses: Seq[String],
      types: Seq[String])

  case class Market(
      baseToken: String,
      quoteToken: String)

  case class SubscribeOrder(
      addresses: Seq[String],
      market: Market = null)

  case class SubscribeTrade(
      addresses: Seq[String],
      market: Market = null)

  case class SubscribeTicker(market: Market = null)

  case class SubscribeOrderBook(
      level: Int,
      market: Market)

  case class TokenBalanceAndAllowance(
      address: String,
      balance: String,
      allowance: String,
      availableBalance: String,
      availableAllowance: String)

  case class BalanceAndAllowanceResponse(
      owner: String,
      balanceAndAllowance: TokenBalanceAndAllowance)

  case class Transaction(
      from: String,
      to: String,
      value: String,
      gasPrice: String,
      gasLimit: String,
      gasUsed: String = "0x0",
      data: String,
      nonce: String,
      hash: String,
      blockNum: String,
      time: String,
      status: String,
      `type`: String)

  case class TransactionResponse(
      owner: String,
      transaction: Transaction)

  case class Order(
      hash: String,
      owner: String,
      version: Int,
      tokenS: String,
      tokenB: String,
      amountS: String,
      amountB: String,
      validSince: String,
      dualAuthAddr: String,
      broker: String,
      orderInterceptor: String,
      wallet: String,
      validUntil: String,
      allOrNone: Boolean,
      tokenFee: String,
      amountFee: String,
      tokenRecipient: String,
      status: String,
      createdAt: String,
      outstandingAmountS: String,
      outstandingAmountB: String,
      outstandingAmountFee: String)

  case class Trade(
      owner: String,
      orderHash: String,
      ringHash: String,
      ringIndex: String,
      fillIndex: String,
      tokenS: String,
      tokenB: String,
      tokenFee: String,
      amountS: String,
      amountB: String,
      amountFee: String,
      feeAmountS: String,
      feeAmountB: String,
      feeRecipient: String,
      waiveFeePercentage: Int,
      walletSplitPercentage: Int,
      wallet: String,
      time: String,
      blockNum: String,
      txHash: String,
      miner: String)
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

}
