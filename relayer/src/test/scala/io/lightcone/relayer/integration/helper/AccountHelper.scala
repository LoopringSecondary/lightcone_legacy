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
    accountSimpleBalanceAndAvailableMatcher(
      Address.ZERO.toString,
      "20".zeros(18)
    ) and
      accountSimpleBalanceAndAvailableMatcher(
        WETH_TOKEN.address,
        "30".zeros(18)
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        LRC_TOKEN.address,
        "400".zeros(18)
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        dynamicMarketPair.baseToken,
        "50".zeros(18)
      ) and
      accountSimpleBalanceAndAvailableMatcher(
        dynamicMarketPair.quoteToken,
        "60".zeros(18)
      )
  }

  def balanceCheck(
      ethMatcher: TokenBalance,
      wethMatcher: TokenBalance,
      lrcMatcher: TokenBalance,
      baseMatcher: TokenBalance,
      quoteMatcher: TokenBalance
    ) = {
    accountBalanceMatcher(ethMatcher.token, ethMatcher) and
      accountBalanceMatcher(wethMatcher.token, wethMatcher) and
      accountBalanceMatcher(lrcMatcher.token, lrcMatcher) and
      accountBalanceMatcher(baseMatcher.token, baseMatcher) and
      accountBalanceMatcher(quoteMatcher.token, quoteMatcher)
  }
}
