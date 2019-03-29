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
import org.slf4s.Logging
import io.lightcone.relayer.integration.AddedMatchers._

trait AccountHelper extends Logging {
  my: MockHelper =>

  val initialBalance = AccountBalance(
    tokenBalanceMap = Map(
      Address.ZERO.toString -> TokenBalance(
        Address.ZERO.toString,
        "20".zeros(18),
        "1000".zeros(18),
        "20".zeros(18),
        "1000".zeros(18)
      ),
      WETH_TOKEN.address -> TokenBalance(
        WETH_TOKEN.address,
        "30".zeros(18),
        "1000".zeros(18),
        "30".zeros(18),
        "1000".zeros(18)
      ),
      LRC_TOKEN.address -> TokenBalance(
        LRC_TOKEN.address,
        "400".zeros(18),
        "1000".zeros(18),
        "400".zeros(18),
        "1000".zeros(18)
      ),
      "baseToken" -> TokenBalance(
        "baseToken",
        "50".zeros(18),
        "1000".zeros(18),
        "50".zeros(18),
        "1000".zeros(18)
      ),
      "quoteToken" -> TokenBalance(
        "quoteToken",
        "60".zeros(18),
        "1000".zeros(18),
        "60".zeros(18),
        "1000".zeros(18)
      ),
      "otherToken" -> TokenBalance(
        "otherToken",
        "90".zeros(18),
        "1000".zeros(18),
        "90".zeros(18),
        "1000".zeros(18)
      )
    )
  )

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
                    case ETH_TOKEN.address =>
                      initialBalance
                        .tokenBalanceMap(t)
                    case WETH_TOKEN.address =>
                      initialBalance
                        .tokenBalanceMap(t)
                    case LRC_TOKEN.address =>
                      initialBalance
                        .tokenBalanceMap(t)
                    case dynamicMarketPair.baseToken =>
                      initialBalance
                        .tokenBalanceMap("baseToken")
                    case dynamicMarketPair.quoteToken =>
                      initialBalance
                        .tokenBalanceMap("quoteToken")
                    case _ =>
                      initialBalance
                        .tokenBalanceMap("otherToken")
                  }
                  t -> balance
                    .copy(token = t) // reset token for dynamicMarketPair
              }.toMap
            )
          )
        )
    })
  }

  def initializeMatcher(dynamicMarketPair: MarketPair) = {
    accountSimpleBalanceAndAvailableMatcher(
      Address.ZERO.toString,
      initialBalance.tokenBalanceMap(ETH_TOKEN.address).balance
    ) and
      accountSimpleBalanceAndAvailableMatcher(
        WETH_TOKEN.address,
        initialBalance.tokenBalanceMap(WETH_TOKEN.address).balance
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        LRC_TOKEN.address,
        initialBalance.tokenBalanceMap(LRC_TOKEN.address).balance
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        dynamicMarketPair.baseToken,
        initialBalance.tokenBalanceMap("baseToken").balance
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        dynamicMarketPair.quoteToken,
        initialBalance.tokenBalanceMap("quoteToken").balance
      )
  }

  def balanceMatcher(
      ethExpect: TokenBalance,
      wethExpect: TokenBalance,
      lrcExpect: TokenBalance,
      baseExpect: TokenBalance,
      quoteExpect: TokenBalance
    ) = {
    accountBalanceMatcher(ethExpect.token, ethExpect) and
      accountBalanceMatcher(wethExpect.token, wethExpect) and
      accountBalanceMatcher(lrcExpect.token, lrcExpect) and
      accountBalanceMatcher(baseExpect.token, baseExpect) and
      accountBalanceMatcher(quoteExpect.token, quoteExpect)
  }
}
