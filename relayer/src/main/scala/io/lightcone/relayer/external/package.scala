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

import io.lightcone.core.Currency

package object external {

  val USD_RMB = s"${Currency.USD.name}-${Currency.RMB.name}"
  val USD_JPY = s"${Currency.USD.name}-${Currency.JPY.name}"
  val USD_EUR = s"${Currency.USD.name}-${Currency.EUR.name}"
  val USD_GBP = s"${Currency.USD.name}-${Currency.GBP.name}"

  val CURRENCY_EXCHANGE_PAIR = Seq(
    USD_RMB,
    USD_JPY,
    USD_EUR,
    USD_GBP
  )

  val QUOTE_TOKEN = Seq(Currency.BTC.name, Currency.ETH.name)
}
