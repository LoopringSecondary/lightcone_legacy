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

package org.loopring.lightcone.biz.model

case class ExchangeTicker(
  symbol: String = "",
  market: String = "",
  exchange: String = "",
  price: Double = 0,
  priceUsd: Double = 0,
  priceCny: Double = 0,
  volume24HUsd: Double = 0,
  volume24HFrom: Double = 0,
  volume24H: Double = 0,
  percentChangeUtc0: Double = 0,
  alias: String = "",
  lastUpdated: Long = 0)
