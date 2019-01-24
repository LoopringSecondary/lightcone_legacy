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

import scala.io.Source
import org.ethereum.solidity.{ Abi => SABI }
import org.web3j.utils.Numeric

import scala.annotation.meta.field

class TradeHistoryAbi(abiJson: String) extends AbiWrap(abiJson) {

  val filled = FilledFunction(
    abi.findFunction(searchByName(FilledFunction.name)))

  val cancelled = CancelledFunction(
    abi.findFunction(searchByName(CancelledFunction.name)))

  val cutoffForMarketKeyBroker = CutoffForMarketKeyBrokerFunction(
    abi.findFunction(searchByName(CutoffForMarketKeyBrokerFunction.name)))

  val cutoffForOwner = CutoffForOwnerFunction(
    abi.findFunction(searchByName(CutoffForOwnerFunction.name)))

  val cutoffForMarketKeyOwner = CutoffForMarketKeyOwnerFunction(
    abi.findFunction(searchByName(CutoffForMarketKeyOwnerFunction.name)))

  val cutoffForBroker = CutoffForBrokerFunction(
    abi.findFunction(searchByName(CutoffForBrokerFunction.name)))

  override def unpackEvent(
    data: String,
    topics: Array[String]): Option[Any] = None

  override def unpackFunctionInput(data: String): Option[Any] = {

    try {
      val funSig =
        Numeric.hexStringToByteArray(
          Numeric.cleanHexPrefix(data).substring(0, 8))
      val func = abi.findFunction(searchBySignature(funSig))
      func match {
        case _: SABI.Function =>
          func.name match {
            case FilledFunction.name =>
              filled.unpackInput(data)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }

  }
}

object TradeHistoryAbi {

  val jsonStr: String =
    "[{\"constant\":false,\"inputs\":[],\"name\":\"resume\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"broker\",\"type\":\"address\"},{\"name\":\"cutoff\",\"type\":\"uint256\"}],\"name\":\"setCutoffs\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"bytes32\"}],\"name\":\"filled\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"kill\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"addr\",\"type\":\"address\"}],\"name\":\"authorizeAddress\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"filledInfo\",\"type\":\"bytes32[]\"}],\"name\":\"batchUpdateFilled\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"broker\",\"type\":\"address\"},{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"cutoff\",\"type\":\"uint256\"}],\"name\":\"setCutoffsOfOwner\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"orderInfo\",\"type\":\"bytes32[]\"}],\"name\":\"batchGetFilledAndCheckCancelled\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256[]\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"bytes20\"}],\"name\":\"marketKeyCutoffsOwner\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"bytes20\"}],\"name\":\"marketKeyCutoffs\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"broker\",\"type\":\"address\"},{\"name\":\"marketKey\",\"type\":\"bytes20\"},{\"name\":\"cutoff\",\"type\":\"uint256\"}],\"name\":\"setTradingPairCutoffs\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"addr\",\"type\":\"address\"}],\"name\":\"isAddressAuthorized\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"broker\",\"type\":\"address\"},{\"name\":\"orderHash\",\"type\":\"bytes32\"}],\"name\":\"setCancelled\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"bytes32\"}],\"name\":\"cancelled\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"cutoffs\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"suspend\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"broker\",\"type\":\"address\"},{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"marketKey\",\"type\":\"bytes20\"},{\"name\":\"cutoff\",\"type\":\"uint256\"}],\"name\":\"setTradingPairCutoffsOfOwner\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"addr\",\"type\":\"address\"}],\"name\":\"deauthorizeAddress\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"address\"}],\"name\":\"cutoffsOwner\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"}]"

  def apply(abiJson: String): TradeHistoryAbi = new TradeHistoryAbi(abiJson)

  def apply(): TradeHistoryAbi = new TradeHistoryAbi(jsonStr)
}

class FilledFunction(val entry: SABI.Function)
  extends AbiFunction[FilledFunction.Params, FilledFunction.Result]

object FilledFunction {

  val name = "filled"

  case class Params(
    @(ContractAnnotation @field)("orderHash", 0) orderHash: Array[Byte])

  case class Result(@(ContractAnnotation @field)("amount", 0) amount: BigInt)

  def apply(entry: SABI.Function): FilledFunction = new FilledFunction(entry)
}

class CancelledFunction(val entry: SABI.Function)
  extends AbiFunction[CancelledFunction.Params, CancelledFunction.Result]

object CancelledFunction {
  val name = "cancelled"
  case class Params(
    @(ContractAnnotation @field)("broker", 0) broker: String,
    @(ContractAnnotation @field)("orderHash", 1) orderHash: Array[Byte])

  case class Result(
    @(ContractAnnotation @field)("cancelled", 0) cancelled: Boolean)

  def apply(entry: SABI.Function): CancelledFunction =
    new CancelledFunction(entry)
}

class CutoffForMarketKeyBrokerFunction(val entry: SABI.Function)
  extends AbiFunction[CutoffForMarketKeyBrokerFunction.Params, CutoffForMarketKeyBrokerFunction.Result]

object CutoffForMarketKeyBrokerFunction {

  val name = "marketKeyCutoffs"

  case class Params(
    @(ContractAnnotation @field)("broker", 0) broker: String,
    @(ContractAnnotation @field)("marketKey", 1) marketKey: Array[Byte])

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffForMarketKeyBrokerFunction =
    new CutoffForMarketKeyBrokerFunction(entry)
}

class CutoffForOwnerFunction(val entry: SABI.Function)
  extends AbiFunction[CutoffForOwnerFunction.Params, CutoffForOwnerFunction.Result]

object CutoffForOwnerFunction {
  val name = "cutoffsOwner"

  case class Params(
    @(ContractAnnotation @field)("broker", 0) broker: String,
    @(ContractAnnotation @field)("owner", 1) owner: String)

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffForOwnerFunction =
    new CutoffForOwnerFunction(entry)
}

class CutoffForMarketKeyOwnerFunction(val entry: SABI.Function)
  extends AbiFunction[CutoffForMarketKeyOwnerFunction.Params, CutoffForMarketKeyOwnerFunction.Result]

object CutoffForMarketKeyOwnerFunction {
  val name = "marketKeyCutoffsOwner"

  case class Params(
    @(ContractAnnotation @field)("broker", 0) broker: String,
    @(ContractAnnotation @field)("owner", 1) owner: String,
    @(ContractAnnotation @field)("marketKey", 2) marketKey: Array[Byte])

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffForMarketKeyOwnerFunction =
    new CutoffForMarketKeyOwnerFunction(entry)
}

class CutoffForBrokerFunction(val entry: SABI.Function)
  extends AbiFunction[CutoffForBrokerFunction.Params, CutoffForBrokerFunction.Result]

object CutoffForBrokerFunction {
  val name = "cutoffs"

  case class Params(@(ContractAnnotation @field)("broker", 0) broker: String)

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffForBrokerFunction =
    new CutoffForBrokerFunction(entry)
}
