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

package io.lightcone.relayer.integration.helper

import io.lightcone.core.MarketPair
import io.lightcone.lib.Address
import io.lightcone.lib.NumericConversion.toBigInt
import io.lightcone.relayer.data.{AccountBalance, GetAccount}
import io.lightcone.relayer.integration.AddedMatchers.check
import io.lightcone.relayer.integration.Metadatas.{
  ETH_TOKEN,
  LRC_TOKEN,
  WETH_TOKEN
}
import io.lightcone.relayer.integration._
import org.slf4s.Logging

trait AccountHelper extends Logging {
  my: MockHelper =>

  def mockAccountWithFixedBalance(
      address: String,
      dynamicMarketPair: MarketPair
    ) = {
    addAccountExpects({
      case req =>
        GetAccount.Res(
          Some(
            AccountBalance(
              address = req.address,
              tokenBalanceMap = req.tokens.map {
                t =>
                  val balance = t match {
                    case ETH_TOKEN.address            => "20".zeros(18)
                    case WETH_TOKEN.address           => "30".zeros(18)
                    case LRC_TOKEN.address            => "400".zeros(18)
                    case dynamicMarketPair.baseToken  => "50".zeros(18)
                    case dynamicMarketPair.quoteToken => "60".zeros(18)
                    case _                            => "90".zeros(18)
                  }
                  t -> AccountBalance.TokenBalance(
                    token = t,
                    balance = balance,
                    allowance = "1000".zeros(18),
                    availableAlloawnce = "1000".zeros(18),
                    availableBalance = balance
                  )
              }.toMap
            )
          )
        )
    })
  }

  def initializeCheck(dynamicMarketPair: MarketPair) = {
    check((res: GetAccount.Res) => {
      val balanceOpt = res.accountBalance
      val ethBalance = toBigInt(
        balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
      )
      val ethAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(Address.ZERO.toString)
          .availableBalance
          .get
      )
      val baseBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.baseToken)
          .balance
          .get
      )
      val baseAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.baseToken)
          .availableBalance
          .get
      )
      val quoteBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.quoteToken)
          .balance
          .get
      )
      val quoteAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.quoteToken)
          .availableBalance
          .get
      )
      val lrcBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(LRC_TOKEN.address)
          .balance
          .get
      )
      val lrcAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(LRC_TOKEN.address)
          .availableBalance
          .get
      )
      ethBalance == "20"
        .zeros(18) && ethBalance == ethAvailableBalance && baseBalance == "50"
        .zeros(18) && baseBalance == baseAvailableBalance && quoteBalance == "60"
        .zeros(18) && quoteBalance == quoteAvailableBalance && lrcBalance == "400"
        .zeros(18) && lrcBalance == lrcAvailableBalance
    })
  }

  def balanceCheck(
      dynamicMarketPair: MarketPair,
      balances: Seq[String]
    ) = {
    check((res: GetAccount.Res) => {
      val balanceOpt = res.accountBalance
      val ethBalance = toBigInt(
        balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
      )
      val ethAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(Address.ZERO.toString)
          .availableBalance
          .get
      )
      val baseBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.baseToken)
          .balance
          .get
      )
      val baseAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.baseToken)
          .availableBalance
          .get
      )
      val quoteBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.quoteToken)
          .balance
          .get
      )
      val quoteAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.quoteToken)
          .availableBalance
          .get
      )
      val lrcBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(LRC_TOKEN.address)
          .balance
          .get
      )
      val lrcAvailableBalance = toBigInt(
        balanceOpt.get
          .tokenBalanceMap(LRC_TOKEN.address)
          .availableBalance
          .get
      )
      ethBalance == balances.head.zeros(18) && ethAvailableBalance == balances(
        1
      ).zeros(18) && baseBalance == balances(2).zeros(18) &&
      baseAvailableBalance == balances(3).zeros(18) && quoteBalance == balances(
        4
      ).zeros(18) && quoteAvailableBalance == balances(5).zeros(18) &&
      lrcBalance == balances(6).zeros(18) && lrcAvailableBalance == balances(7)
        .zeros(18)
    })
  }

  def wethBalanceCheck(
      wethBalance: String,
      wethAvailableBalance: String
    ) = {
    check((res: GetAccount.Res) => {
      val balanceOpt = res.accountBalance
      val balance = toBigInt(
        balanceOpt.get.tokenBalanceMap(WETH_TOKEN.address).balance.get
      )
      val availableBalance = toBigInt(
        balanceOpt.get.tokenBalanceMap(WETH_TOKEN.address).availableBalance.get
      )
      balance == wethBalance
        .zeros(18) && availableBalance == wethAvailableBalance.zeros(18)
    })
  }
}
