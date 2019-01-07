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

import org.ethereum.solidity.{Abi => SABI}
import org.web3j.utils.Numeric

import scala.annotation.meta.field
import scala.io.Source

class BurnRateTableAbi(abiJson: String) extends AbiWrap(abiJson) {

  val getBurnRate = GetBurnRateFunction(
    abi.findFunction(searchByName(GetBurnRateFunction.name))
  )

  val burn_BASE_PERCENTAGE = BURN_BASE_PERCENTAGEFunction(
    abi.findFunction(searchByName(BURN_BASE_PERCENTAGEFunction.name))
  )

  val tokenTierUpgradedEvent = TokenTierUpgradedEvent(
    abi.findEvent(searchByName(TokenTierUpgradedEvent.name))
  )

  override def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] = {
    val event: SABI.Event = abi.findEvent(
      searchBySignature(Numeric.hexStringToByteArray(topics.head))
    )
    event match {
      case _: SABI.Event =>
        event.name match {
          case TokenTierUpgradedEvent.name =>
            tokenTierUpgradedEvent.unpack(data, topics)
          case _ => None
        }
      case _ => None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = None

}

object BurnRateTableAbi {

  val jsonStr: String = Source
    .fromFile("ethereum/src/main/resources/version20/IBurnRateTable.abi")
    .getLines()
    .next()

  def apply(abiJson: String): BurnRateTableAbi = new BurnRateTableAbi(abiJson)

  def apply(): BurnRateTableAbi = new BurnRateTableAbi(jsonStr)
}

class GetBurnRateFunction(val entry: SABI.Function)
    extends AbiFunction[GetBurnRateFunction.Params, GetBurnRateFunction.Result]

object GetBurnRateFunction {

  val name = "getBurnRate"

  case class Params(@(ContractAnnotation @field)("token", 0) token: String)

  case class Result(
      @(ContractAnnotation @field)("burnRate", 0) burnRate: BigInt)

  def apply(entry: SABI.Function): GetBurnRateFunction =
    new GetBurnRateFunction(entry)
}

class BURN_BASE_PERCENTAGEFunction(val entry: SABI.Function)
    extends AbiFunction[
      BURN_BASE_PERCENTAGEFunction.Params,
      BURN_BASE_PERCENTAGEFunction.Result
    ]

object BURN_BASE_PERCENTAGEFunction {

  val name = "BURN_BASE_PERCENTAGE"

  case class Params()

  case class Result(@(ContractAnnotation @field)("base", 0) burnRate: BigInt)

  def apply(entry: SABI.Function): BURN_BASE_PERCENTAGEFunction =
    new BURN_BASE_PERCENTAGEFunction(entry)
}

class TokenTierUpgradedEvent(val entry: SABI.Event)
    extends AbiEvent[TokenTierUpgradedEvent.Result]

object TokenTierUpgradedEvent {

  val name = "TokenTierUpgraded"

  case class Result(
      @(ContractAnnotation @field)("add", 0) add: String,
      @(ContractAnnotation @field)("tier", 1) tier: BigInt)

  def apply(entry: SABI.Event): TokenTierUpgradedEvent =
    new TokenTierUpgradedEvent(entry)
}
