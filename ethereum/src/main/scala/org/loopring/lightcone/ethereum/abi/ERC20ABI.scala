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

object ERC20ABI {

  val erc20jsonstr =
    "[{\"constant\":false,\"inputs\":[{\"name\":\"spender\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"tx_from\",\"type\":\"address\"},{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"who\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"spender\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"owner\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"spender\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"tx_from\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"to\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"}]"

  def apply(): ERC20ABI = new ERC20ABI(erc20jsonstr)

  def apply(jsonstr: String) = new ERC20ABI(jsonstr)
}

class ERC20ABI(abiJson: String) extends AbiWrap(abiJson) {

  val transfer = TransferFunction(
    abi.findFunction(searchByName(TransferFunction.name))
  )

  val transferFrom = TransferFunction(
    abi.findFunction(searchByName(TransferFromFunction.name))
  )

  val approve = ApproveFunction(
    abi.findFunction(searchByName(ApproveFunction.name))
  )

  val balanceOf = BalanceOfFunction(
    abi.findFunction(searchByName(BalanceOfFunction.name))
  )

  val allowance = AllowanceFunction(
    abi.findFunction(searchByName(AllowanceFunction.name))
  )

  val transferEvent = TransferEvent(
    abi.findEvent(searchByName(TransferEvent.name))
  )

  val approvalEvent = ApprovalEvent(
    abi.findEvent(searchByName(ApprovalEvent.name))
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
          case ApprovalEvent.name =>
            approvalEvent.unpack(data, topics)
          case TransferEvent.name =>
            transferEvent.unpack(data, topics)
          case _ => None
        }
      case _ => None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = {
    val funSig =
      Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(data).substring(0, 8))
    val func = abi.findFunction(searchBySignature(funSig))
    func match {
      case _: SABI.Function =>
        func.name match {
          case TransferFunction.name =>
            transfer.unpackInput(data)
          case TransferFromFunction.name =>
            transferFrom.unpackInput(data)
          case ApproveFunction.name =>
            approve.unpackInput(data)
          case BalanceOfFunction.name =>
            balanceOf.unpackInput(data)
          case AllowanceFunction.name =>
            allowance.unpackInput(data)
          case _ => None
        }
      case _ => None
    }
  }

}

//-------- define of contract's method and event
object TransferFunction {

  case class Parms(
      @(ContractAnnotation @field)("to", 0) to: String,
      @(ContractAnnotation @field)("amount", 1) amount: BigInt)

  case class Result()

  val name = "transfer"

  def apply(function: SABI.Function): TransferFunction =
    new TransferFunction(function)
}

class TransferFunction(val entry: SABI.Function)
    extends AbiFunction[TransferFunction.Parms, TransferFunction.Result]

object TransferFromFunction {

  case class Parms(
      @(ContractAnnotation @field)("tx_from", 0) txFrom: String,
      @(ContractAnnotation @field)("to", 1) to: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt)

  case class Result()

  val name = "transferFrom"

  def apply(function: SABI.Function): TransferFromFunction =
    new TransferFromFunction(function)
}

class TransferFromFunction(val entry: SABI.Function)
    extends AbiFunction[TransferFromFunction.Parms, TransferFromFunction.Result]

object ApproveFunction {

  case class Parms(
      @(ContractAnnotation @field)("spender", 0) spender: String,
      @(ContractAnnotation @field)("amount", 1) amount: BigInt)

  case class Result()

  val name = "approve"

  def apply(function: SABI.Function): ApproveFunction =
    new ApproveFunction(function)
}

class ApproveFunction(val entry: SABI.Function)
    extends AbiFunction[ApproveFunction.Parms, ApproveFunction.Result]

object BalanceOfFunction {
  case class Parms(@(ContractAnnotation @field)("_owner", 0) _owner: String)
  val name = "balanceOf"

  case class Result(@(ContractAnnotation @field)("balance", 0) balance: BigInt)

  def apply(entry: SABI.Function): BalanceOfFunction =
    new BalanceOfFunction(entry)
}

class BalanceOfFunction(val entry: SABI.Function)
    extends AbiFunction[BalanceOfFunction.Parms, BalanceOfFunction.Result]

object AllowanceFunction {

  case class Parms(
      @(ContractAnnotation @field)("_owner", 0) _owner: String,
      @(ContractAnnotation @field)("_spender", 1) _spender: String)
  val name = "allowance"
  case class Result(
      @(ContractAnnotation @field)("allowance", 0) allowance: BigInt)

  def apply(entry: SABI.Function): AllowanceFunction =
    new AllowanceFunction(entry)
}

class AllowanceFunction(val entry: SABI.Function)
    extends AbiFunction[AllowanceFunction.Parms, AllowanceFunction.Result]

object TransferEvent {
  val name = "Transfer"

  def apply(event: SABI.Event): TransferEvent = new TransferEvent(event)

  case class Result(
      @(ContractAnnotation @field)("to", 0) sender: String,
      @(ContractAnnotation @field)("receiver", 1) receiver: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt)

}

class TransferEvent(val entry: SABI.Event)
    extends AbiEvent[TransferEvent.Result]

object ApprovalEvent {
  val name = "Approval"

  def apply(event: SABI.Event): ApprovalEvent = new ApprovalEvent(event)

  case class Result(
      @(ContractAnnotation @field)("owner", 0) owner: String,
      @(ContractAnnotation @field)("spender", 1) spender: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt)
}

class ApprovalEvent(val entry: SABI.Event)
    extends AbiEvent[ApprovalEvent.Result]
