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

object WETHABI {

  val abijsonstr =
    "[{\"constant\":true,\"inputs\":[],\"name\":\"name\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"guy\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"src\",\"type\":\"address\"},{\"name\":\"dst\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"withdraw\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"decimals\",\"outputs\":[{\"name\":\"\",\"type\":\"uint8\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"symbol\",\"outputs\":[{\"name\":\"\",\"type\":\"string\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"dst\",\"type\":\"address\"},{\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"\",\"type\":\"address\"},{\"name\":\"\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"guy\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"dst\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"dst\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Deposit\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"src\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"wad\",\"type\":\"uint256\"}],\"name\":\"Withdrawal\",\"type\":\"event\"}]"

  def apply(): WETHABI = new WETHABI(abijsonstr)

  def apply(jsonstr: String) = new WETHABI(jsonstr)
}

class WETHABI(abiJson: String) extends ERC20ABI(abiJson) {

  val deposit = DepositFunction(
    abi.findFunction(searchByName(DepositFunction.name))
  )

  val withdraw = WithdrawFunction(
    abi.findFunction(searchByName(WithdrawFunction.name))
  )

  override def unpackEvent(data: String, topics: Array[String]): Option[Any] = {
    val event: SABI.Event = abi.findEvent(searchBySignature(Numeric.hexStringToByteArray(topics.head)))
    event match {
      case _: SABI.Event ⇒
        event.name match {
          case ApprovalEvent.name ⇒
            approvalEvent.unpack(data, topics)
          case TransferEvent.name ⇒
            transferEvent.unpack(data, topics)
          case DepositEvent.name ⇒
            depositEvent.unpack(data, topics)
          case WithdrawalEvent.name ⇒
            withdrawalEvent.unpack(data, topics)
          case _ ⇒ None
        }
      case _ ⇒ None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = {
    val funSig = Numeric.hexStringToByteArray(Numeric.cleanHexPrefix(data).substring(0, 8))
    val func = abi.findFunction(searchBySignature(funSig))
    func match {
      case _: SABI.Function ⇒
        func.name match {
          case TransferFunction.name ⇒
            transfer.unpackInput(data)
          case TransferFromFunction.name ⇒
            transferFrom.unpackInput(data)
          case ApproveFunction.name ⇒
            approve.unpackInput(data)
          case BalanceOfFunction.name ⇒
            balanceOf.unpackInput(data)
          case AllowanceFunction.name ⇒
            allowance.unpackInput(data)
          case DepositFunction.name ⇒
            deposit.unpackInput(data)
          case WithdrawFunction.name ⇒
            withdraw.unpackInput(data)
          case _ ⇒ None
        }
      case _ ⇒ None
    }
  }

}

object DepositFunction {

  case class Parms()

  val name = "deposit"

  case class Result()

  def apply(entry: SABI.Function): DepositFunction = new DepositFunction(entry)
}

class DepositFunction(val entry: SABI.Function)
    extends AbiFunction[DepositFunction.Parms, DepositFunction.Result]

object WithdrawFunction {

  case class Parms(@(ContractAnnotation @field)("wad", 0) wad: BigInt)

  val name = "withdraw"

  case class Result()

  def apply(entry: SABI.Function): WithdrawFunction =
    new WithdrawFunction(entry)
}

class WithdrawFunction(val entry: SABI.Function)
    extends AbiFunction[WithdrawFunction.Parms, WithdrawFunction.Result]

object DepositEvent {

  case class Parms()

  val name = "Deposit"

  case class Result(
      @(ContractAnnotation @field)("dst", 0) dst: String,
      @(ContractAnnotation @field)("wad", 1) wad: BigInt)

  def apply(entry: SABI.Event): DepositEvent = new DepositEvent(entry)
}

class DepositEvent(val entry: SABI.Event) extends AbiEvent[DepositEvent.Result]

object WithdrawalEvent {

  case class Prams()

  val name = "Withdrawal"

  case class Result(
      @(ContractAnnotation @field)("src", 0) src: String,
      @(ContractAnnotation @field)("wad", 1) wad: BigInt)

  def apply(entry: SABI.Event): WithdrawalEvent = new WithdrawalEvent(entry)
}

class WithdrawalEvent(val entry: SABI.Event)
    extends AbiEvent[WithdrawalEvent.Result]
