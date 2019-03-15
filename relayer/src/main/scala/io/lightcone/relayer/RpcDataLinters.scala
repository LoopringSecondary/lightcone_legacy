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

import io.lightcone.core.{Market, MarketMetadata, RawOrder, TokenInfo, TokenMetadata}
import io.lightcone.relayer.data.AccountBalance.TokenBalance
import io.lightcone.relayer.data._
import io.lightcone.relayer.jsonrpc.Linter

object RpcDataLinters {

  implicit val submitOrderResLinter = new Linter[SubmitOrder.Res] {

    def lint(data: SubmitOrder.Res) = SubmitOrder.Res(success = data.success)
  }

  implicit val rawOrderLinter = new Linter[RawOrder] {

    def lint(order: RawOrder) =
      RawOrder(
        hash = order.hash,
        version = order.version,
        owner = order.owner,
        tokenS = order.tokenS,
        tokenB = order.tokenB,
        amountS = order.amountS,
        amountB = order.amountB,
        validSince = order.validSince,
        params = order.params.map(
          param =>
            RawOrder.Params(
              broker = param.broker,
              orderInterceptor = param.orderInterceptor,
              wallet = param.wallet,
              validUntil = param.validUntil,
              allOrNone = param.allOrNone
            )
        ),
        feeParams = order.feeParams.map(
          param =>
            RawOrder.FeeParams(
              tokenFee = param.tokenFee,
              amountFee = param.amountFee,
              tokenSFeePercentage = param.tokenSFeePercentage,
              tokenBFeePercentage = param.tokenBFeePercentage,
              tokenRecipient = param.tokenRecipient
            )
        ),
        state = order.state
      )
  }

  implicit val getOrdersResLinter = new Linter[GetOrders.Res] {

    def lint(data: GetOrders.Res) =
      data.copy(orders = data.orders.map(rawOrderLinter.lint))
  }

  implicit val tokenBalanceLinter = new Linter[TokenBalance] {

    def lint(data: TokenBalance): TokenBalance =
      TokenBalance(
        token = data.token,
        balance = data.balance,
        allowance = data.allowance,
        block = data.block
      )
  }

  implicit val accountUpdateLinter = new Linter[AccountUpdate] {

    def lint(data: AccountUpdate): AccountUpdate =
      AccountUpdate(
        address = data.address,
        tokenBalance = data.tokenBalance.map(tokenBalanceLinter.lint)
      )
  }

  implicit val marketMetadataLinter = new Linter[MarketMetadata] {

    def lint(data: MarketMetadata): MarketMetadata =
      MarketMetadata(
        status = data.status,
        priceDecimals = data.priceDecimals,
        orderbookAggLevels = data.orderbookAggLevels,
        precisionForAmount = data.precisionForAmount,
        precisionForTotal = data.precisionForTotal,
        browsableInWallet = data.browsableInWallet,
        marketPair = data.marketPair,
        marketHash = data.marketHash
      )
  }

  implicit val getMarketsResLinter = new Linter[GetMarkets.Res] {

    def lint(data: GetMarkets.Res): GetMarkets.Res = {
      GetMarkets.Res(
        markets = data.markets.map(
          market =>
            market.copy(
              metadata = market.metadata.map(marketMetadataLinter.lint)
            )
        )
      )
    }
  }

  implicit val tokenMetadataLinter = new Linter[TokenMetadata] {

    def lint(data: TokenMetadata): TokenMetadata =
      TokenMetadata(
        `type` = data.`type`,
        status = data.status,
        symbol = data.symbol,
        name = data.name,
        address = data.address,
        unit = data.unit,
        decimals = data.decimals,
        precision = data.precision,
        burnRate = data.burnRate
      )

  }

  implicit val tokenInfoLinter = new Linter[TokenInfo] {
     def lint(data: TokenInfo): TokenInfo = data.withUpdatedAt(0L)
  }

  implicit val getTokensResLinter = new Linter[GetTokens.Res] {

    def lint(data: GetTokens.Res): GetTokens.Res = {

      GetTokens.Res(
        tokens = data.tokens.map(
          token =>
            token.copy(
              metadata = token.metadata.map(tokenMetadataLinter.lint),
              info = token.info.map(tokenInfoLinter.lint))
        )
      )
    }
  }

}
