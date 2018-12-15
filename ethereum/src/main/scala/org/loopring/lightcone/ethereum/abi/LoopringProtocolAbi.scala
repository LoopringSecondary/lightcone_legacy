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

package org.loopring.lightcone.ethereum.abi

case class LoopringProtocolAbi() {

  val abis: Seq[AbiWrap] = Seq(
    OrderBookAbi(),
    OrderCancellerAbi(),
    RingSubmitterAbi(),
    BurnRateTableAbi(),
    AuthorizableAbi(),
    TradeHistoryAbi()
  )

  def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] =
    abis
      .map(abi ⇒ abi.unpackEvent(data, topics))
      .find(_.nonEmpty)
      .flatten

  def unpackFunctionInput(input: String): Option[Any] =
    abis
      .map(abi ⇒ abi.unpackFunctionInput(input))
      .find(_.nonEmpty)
      .flatten
}
