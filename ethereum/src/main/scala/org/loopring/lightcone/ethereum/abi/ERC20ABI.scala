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

import org.ethereum.solidity.{ Abi ⇒ SABI }

import scala.annotation.meta.field

object ERC20ABI {
  val erc20jsonstr = "[{\"constant\":false,\"inputs\":[{\"name\":\"spender\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"tx_from\",\"type\":\"address\"},{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"who\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"spender\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"owner\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"spender\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"tx_from\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"to\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"}]"

  def apply(): ERC20ABI = new ERC20ABI(erc20jsonstr)

  def apply(jsonstr: String) = new ERC20ABI(jsonstr)
}

class ERC20ABI(abiJson: String) extends AbiWrap(abiJson) {

  val transfer = TransferFunction(abi.findFunction(searchByName(TransferFunction.name)))
  val transferFrom = TransferFunction(abi.findFunction(searchByName(TransferFromFunction.name)))
  val approve = ApproveFunction(abi.findFunction(searchByName(ApproveFunction.name)))

  val transferEvent = TransferEvent(abi.findEvent(searchByName(TransferEvent.name)))
  val approvalEvent = ApprovalEvent(abi.findEvent(searchByName(ApprovalEvent.name)))

}

//-------- define of contract's method and event
object TransferFunction {

  case class Parms(
      @(ContractAnnotation @field)("to", 0) to: String,
      @(ContractAnnotation @field)("amount", 1) amount: BigInt
  )

  case class Result()

  val name = "transfer"

  def apply(function: SABI.Function): TransferFunction = new TransferFunction(function)
}

class TransferFunction(val entry: SABI.Function) extends AbiFunction[TransferFunction.Parms, TransferFunction.Result]

object TransferFromFunction {

  case class Parms(
      @(ContractAnnotation @field)("tx_from", 0) txFrom: String,
      @(ContractAnnotation @field)("to", 1) to: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt
  )

  case class Result()

  val name = "transferFrom"

  def apply(function: SABI.Function): TransferFromFunction = new TransferFromFunction(function)
}

class TransferFromFunction(val entry: SABI.Function) extends AbiFunction[TransferFromFunction.Parms, TransferFromFunction.Result]

object ApproveFunction {

  case class Parms(
      @(ContractAnnotation @field)("spender", 0) spender: String,
      @(ContractAnnotation @field)("amount", 1) amount: BigInt
  )

  case class Result()

  val name = "approve"

  def apply(function: SABI.Function): ApproveFunction = new ApproveFunction(function)
}

class ApproveFunction(val entry: SABI.Function) extends AbiFunction[ApproveFunction.Parms, ApproveFunction.Result]

object TransferEvent {
  val name = "Transfer"

  def apply(event: SABI.Event): TransferEvent = new TransferEvent(event)

  case class Result(
      @(ContractAnnotation @field)("to", 0) sender: String,
      @(ContractAnnotation @field)("receiver", 1) receiver: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt
  )

}

class TransferEvent(val entry: SABI.Event) extends AbiEvent[TransferEvent.Result]

object ApprovalEvent {
  val name = "Approval"

  def apply(event: SABI.Event): ApprovalEvent = new ApprovalEvent(event)

  case class Result(
      @(ContractAnnotation @field)("owner", 0) owner: String,
      @(ContractAnnotation @field)("spender", 1) spender: String,
      @(ContractAnnotation @field)("amount", 2) amount: BigInt
  )
}

class ApprovalEvent(val entry: SABI.Event) extends AbiEvent[ApprovalEvent.Result]

