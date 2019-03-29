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
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data.{AccountBalance, GetAccount}
import io.lightcone.relayer.integration.Metadatas._
import io.lightcone.relayer.integration._
import org.scalatest.matchers.{MatchResult, Matcher}
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
    Matcher { res: GetAccount.Res =>
      val balanceOpt = res.accountBalance
      val balance: BigInt =
        balanceOpt.get.tokenBalanceMap(Address.ZERO.toString).balance.get
      val availableBalance: BigInt =
        balanceOpt.get
          .tokenBalanceMap(Address.ZERO.toString)
          .availableBalance
          .get
      MatchResult(
        balance == "20".zeros(18) && balance == availableBalance,
        s" ${JsonPrinter.printJsonString(
          res.getAccountBalance.tokenBalanceMap(Address.ZERO.toString)
        )} balance and availableBalance not equal to ${"20".zeros(18)}.",
        s"accountBalance of ETH matches."
      )
    } and Matcher { res: GetAccount.Res =>
      val balanceOpt = res.accountBalance
      val balance: BigInt =
        balanceOpt.get.tokenBalanceMap(WETH_TOKEN.address).balance.get
      val availableBalance: BigInt =
        balanceOpt.get
          .tokenBalanceMap(WETH_TOKEN.address)
          .availableBalance
          .get
      MatchResult(
        balance == "30".zeros(18) && balance == availableBalance,
        s" ${JsonPrinter.printJsonString(
          res.getAccountBalance.tokenBalanceMap(WETH_TOKEN.address)
        )} balance and availableBalance not equal to ${"30".zeros(18)}.",
        s"accountBalance of WETH matches."
      )
    } and Matcher { res: GetAccount.Res =>
      val balanceOpt = res.accountBalance
      val balance: BigInt =
        balanceOpt.get.tokenBalanceMap(LRC_TOKEN.address).balance.get
      val availableBalance: BigInt =
        balanceOpt.get
          .tokenBalanceMap(LRC_TOKEN.address)
          .availableBalance
          .get
      MatchResult(
        balance == "400".zeros(18) && balance == availableBalance,
        s" ${JsonPrinter.printJsonString(
          res.getAccountBalance.tokenBalanceMap(LRC_TOKEN.address)
        )} balance and availableBalance not equal to ${"400".zeros(18)}.",
        s"accountBalance of LRC matches."
      )
    } and Matcher { res: GetAccount.Res =>
      val balanceOpt = res.accountBalance
      val balance: BigInt =
        balanceOpt.get.tokenBalanceMap(dynamicMarketPair.baseToken).balance.get
      val availableBalance: BigInt =
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.baseToken)
          .availableBalance
          .get
      MatchResult(
        balance == "50".zeros(18) && balance == availableBalance,
        s" ${JsonPrinter.printJsonString(
          res.getAccountBalance.tokenBalanceMap(dynamicMarketPair.baseToken)
        )} balance and availableBalance not equal to ${"50".zeros(18)}.",
        s"accountBalance of BaseToken matches."
      )
    } and Matcher { res: GetAccount.Res =>
      val balanceOpt = res.accountBalance
      val balance: BigInt =
        balanceOpt.get.tokenBalanceMap(dynamicMarketPair.quoteToken).balance.get
      val availableBalance: BigInt =
        balanceOpt.get
          .tokenBalanceMap(dynamicMarketPair.quoteToken)
          .availableBalance
          .get
      MatchResult(
        balance == "60".zeros(18) && balance == availableBalance,
        s" ${JsonPrinter.printJsonString(
          res.getAccountBalance.tokenBalanceMap(dynamicMarketPair.quoteToken)
        )} balance and availableBalance not equal to ${"60".zeros(18)}.",
        s"accountBalance of QuoteToken matches."
      )
    }
  }

  def balanceCheck(
      ethMatcher: TokenBalance,
      wethMatcher: TokenBalance,
      lrcMatcher: TokenBalance,
      baseMatcher: TokenBalance,
      quoteMatcher: TokenBalance
    ) = {
    Matcher { res: GetAccount.Res =>
      MatchResult(
        res.getAccountBalance
          .tokenBalanceMap(Address.ZERO.toString) == ethMatcher,
        s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(Address.ZERO.toString))} was not equal to  ${JsonPrinter
          .printJsonString(ethMatcher)}.",
        s"accountBalance of ETH matches."
      )
    } and Matcher { res: GetAccount.Res =>
      MatchResult(
        res.getAccountBalance.tokenBalanceMap(wethMatcher.token) == wethMatcher,
        s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(wethMatcher.token))} was not equal to  ${JsonPrinter
          .printJsonString(wethMatcher)}.",
        s"accountBalance of WETH matches."
      )
    } and Matcher { res: GetAccount.Res =>
      MatchResult(
        res.getAccountBalance.tokenBalanceMap(lrcMatcher.token) == lrcMatcher,
        s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(lrcMatcher.token))} was not equal to  ${JsonPrinter
          .printJsonString(lrcMatcher)}.",
        s"accountBalance of LRC matches."
      )
    } and Matcher { res: GetAccount.Res =>
      MatchResult(
        res.getAccountBalance.tokenBalanceMap(baseMatcher.token) == baseMatcher,
        s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(baseMatcher.token))} was not equal to  ${JsonPrinter
          .printJsonString(baseMatcher)}.",
        s"accountBalance of BaseToken matches."
      )
    } and Matcher { res: GetAccount.Res =>
      MatchResult(
        res.getAccountBalance
          .tokenBalanceMap(quoteMatcher.token) == quoteMatcher,
        s" ${JsonPrinter.printJsonString(res.getAccountBalance.tokenBalanceMap(quoteMatcher.token))} was not equal to  ${JsonPrinter
          .printJsonString(quoteMatcher)}.",
        s"accountBalance of QuoteToken matches."
      )
    }
  }
}
