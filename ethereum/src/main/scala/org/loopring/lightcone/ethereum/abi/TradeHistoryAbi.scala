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
import org.ethereum.solidity.{Abi => SABI}
import org.web3j.utils.Numeric

import scala.annotation.meta.field

class TradeHistoryAbi(abiJson: String) extends AbiWrap(abiJson) {

  val filled = FilledFunction(
    abi.findFunction(searchByName(FilledFunction.name))
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
      case _: SABI.Function =>
        func.name match {
          case FilledFunction.name =>
            filled.unpackInput(data)
          case _ => None
        }
      case _ => None
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
