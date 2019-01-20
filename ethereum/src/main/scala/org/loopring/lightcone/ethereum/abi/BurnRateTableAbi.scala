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
    try {
      val event: SABI.Event = abi.findEvent(
        searchBySignature(
          Numeric.hexStringToByteArray(topics.headOption.getOrElse(""))
        )
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
    } catch {
      case _: Throwable => None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = None

}

object BurnRateTableAbi {

  val jsonStr: String =
    "[{\"constant\":true,\"inputs\":[],\"name\":\"BURN_P2P_TIER2\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"TIER_2\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_MATCHING_TIER4\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_MATCHING_TIER3\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"TIER_4\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"token\",\"type\":\"address\"}],\"name\":\"getBurnRate\",\"outputs\":[{\"name\":\"burnRate\",\"type\":\"uint32\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_MATCHING_TIER1\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_P2P_TIER1\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_P2P_TIER4\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_P2P_TIER3\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"TIER_UPGRADE_COST_PERCENTAGE\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"TIER_1\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"TIER_3\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"YEAR_TO_SECONDS\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"token\",\"type\":\"address\"}],\"name\":\"getTokenTier\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_MATCHING_TIER2\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"tokens\",\"outputs\":[{\"name\":\"tier\",\"type\":\"uint256\"},{\"name\":\"validUntil\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"token\",\"type\":\"address\"}],\"name\":\"upgradeTokenTier\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"BURN_BASE_PERCENTAGE\",\"outputs\":[{\"name\":\"\",\"type\":\"uint16\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"addr\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"tier\",\"type\":\"uint256\"}],\"name\":\"TokenTierUpgraded\",\"type\":\"event\"}]"

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
