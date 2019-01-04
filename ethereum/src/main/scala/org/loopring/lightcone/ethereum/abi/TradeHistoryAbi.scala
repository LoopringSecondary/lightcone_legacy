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
import org.ethereum.solidity.{Abi ⇒ SABI}
import org.web3j.utils.Numeric

import scala.annotation.meta.field

class TradeHistoryAbi(abiJson: String) extends AbiWrap(abiJson) {

  val filled = FilledFunction(
    abi.findFunction(searchByName(FilledFunction.name))
  )

  val cancelled = CancelledFunction(
    abi.findFunction(searchByName(CancelledFunction.name))
  )

  val tradingPairCutoffs = TradingPairCutoffsFunction(
    abi.findFunction(searchByName(TradingPairCutoffsFunction.name))
  )

  val cutoffsOwner = CutoffsOwnerFunction(
    abi.findFunction(searchByName(CutoffsOwnerFunction.name))
  )

  val tradingPairCutoffsOwner = TradingPairCutoffsOwnerFunction(
    abi.findFunction(searchByName(TradingPairCutoffsOwnerFunction.name))
  )

  val cutoffs = CutoffsFunction(
    abi.findFunction(searchByName(CutoffsFunction.name))
  )

  override def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] = None

  override def unpackFunctionInput(data: String): Option[Any] = {
    val funSig =
      Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(data).substring(0, 8))
    val func = abi.findFunction(searchBySignature(funSig))
    func match {
      case _: SABI.Function ⇒
        func.name match {
          case FilledFunction.name ⇒
            filled.unpackInput(data)
          case _ ⇒ None
        }
      case _ ⇒ None
    }
  }
}

object TradeHistoryAbi {

  val jsonStr: String = Source
    .fromFile("ethereum/src/main/resources/version20/ITradeHistory.abi")
    .getLines()
    .next()

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

class TradingPairCutoffsFunction(val entry: SABI.Function)
    extends AbiFunction[
      TradingPairCutoffsFunction.Params,
      TradingPairCutoffsFunction.Result
    ]

object TradingPairCutoffsFunction {

  val name = "tradingPairCutoffs"

  case class Params(
      @(ContractAnnotation @field)("broker", 0) broker: String,
      @(ContractAnnotation @field)("tokenPair", 1) tokenPair: Array[Byte])

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): TradingPairCutoffsFunction =
    new TradingPairCutoffsFunction(entry)
}

class CutoffsOwnerFunction(val entry: SABI.Function)
    extends AbiFunction[
      CutoffsOwnerFunction.Params,
      CutoffsOwnerFunction.Result
    ]

object CutoffsOwnerFunction {
  val name = "cutoffsOwner"

  case class Params(
      @(ContractAnnotation @field)("broker", 0) broker: String,
      @(ContractAnnotation @field)("owner", 1) owner: String)

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffsOwnerFunction =
    new CutoffsOwnerFunction(entry)
}

class TradingPairCutoffsOwnerFunction(val entry: SABI.Function)
    extends AbiFunction[
      TradingPairCutoffsOwnerFunction.Params,
      TradingPairCutoffsOwnerFunction.Result
    ]

object TradingPairCutoffsOwnerFunction {
  val name = "tradingPairCutoffsOwner"

  case class Params(
      @(ContractAnnotation @field)("broker", 0) broker: String,
      @(ContractAnnotation @field)("owner", 1) owner: String,
      @(ContractAnnotation @field)("tokenPair", 2) tokenPair: Array[Byte])

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): TradingPairCutoffsOwnerFunction =
    new TradingPairCutoffsOwnerFunction(entry)
}

class CutoffsFunction(val entry: SABI.Function)
    extends AbiFunction[CutoffsFunction.Params, CutoffsFunction.Result]

object CutoffsFunction {
  val name = "cutoffs"

  case class Params(@(ContractAnnotation @field)("broker", 0) broker: String)

  case class Result(@(ContractAnnotation @field)("cutOff", 0) cutOff: BigInt)

  def apply(entry: SABI.Function): CutoffsFunction = new CutoffsFunction(entry)
}
