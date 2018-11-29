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

package org.loopring.lightcone.lib.abi

import org.loopring.lightcone.lib.data.{Transfer, _}
import org.ethereum.solidity.{Abi ⇒ SABI}

object TransferFunction {

  case class Parms(
                    to: String,
                    amount: BigInt)

  case class Result()

  val name = "transfer"

  def apply(function: SABI.Function): TransferFunction = new TransferFunction(function)
}

class TransferFunction(function: SABI.Function) extends AbiFunction[TransferFunction.Parms, TransferFunction.Result] {
  val entry = function

  def pack(p: TransferFunction.Parms): Array[Byte] = {
    entry.encode(p.to, p.amount)
  }

  def unpackInput(data: Array[Byte]): Option[TransferFunction.Parms] = {
    val list = entry.decode(data)
    if (list.isEmpty)
      None
    else
      Some(TransferFunction.Parms(to = scalaAny2Hex(list.get(0)), amount = scalaAny2Bigint(list.get(1))))
  }

  def unpackResult(data: Array[Byte]): Option[TransferFunction.Result] = None
}

object TransferEvent {
  val name = "Transfer"

  def apply(event: SABI.Event): TransferEvent = new TransferEvent(event)

  case class Result()

}

class TransferEvent(event: SABI.Event) extends AbiEvent[TransferEvent.Result] {
  val entry = event

  def unpack(log: TransactionLog): Option[TransferEvent.Result] = ???

}

object ERC20ABI {
  val erc20jsonstr = "[{\"constant\":false,\"inputs\":[{\"name\":\"spender\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"approve\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"totalSupply\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"tx_from\",\"type\":\"address\"},{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transferFrom\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"who\",\"type\":\"address\"}],\"name\":\"balanceOf\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"to\",\"type\":\"address\"},{\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"transfer\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"owner\",\"type\":\"address\"},{\"name\":\"spender\",\"type\":\"address\"}],\"name\":\"allowance\",\"outputs\":[{\"name\":\"\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"owner\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"spender\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Approval\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":true,\"name\":\"tx_from\",\"type\":\"address\"},{\"indexed\":true,\"name\":\"to\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"value\",\"type\":\"uint256\"}],\"name\":\"Transfer\",\"type\":\"event\"}]"

  def apply(): ERC20ABI = new ERC20ABI(erc20jsonstr)
}

class ERC20ABI(abiJson: String) extends AbiWrap(abiJson) {

  val transfer: TransferFunction = TransferFunction(abi.findFunction(searchByName(TransferFunction.name)))

  val transferEvent: TransferEvent = TransferEvent(abi.findEvent(searchByName(TransferEvent.name)))

  //  functions = Seq(transfer)
  //  events = Seq(transferEvent)


  //  object ApproveFunction {
  //    def apply: ApproveFunction = new ApproveFunction()
  //
  //    case class Parms()
  //    case class Result()
  //  }
  //  class ApproveFunction extends AbiFunction[ApproveFunction.Parms, ApproveFunction.Result] {
  //    val entry = abi.findFunction(searchByName("approve"))
  //
  //    def pack(t: ApproveFunction.Parms): Array[Byte] = ???
  //
  //    def unpackInput(data: Array[Byte]): Option[ApproveFunction.Parms] = ???
  //
  //    def unpackResult(data: Array[Byte]): Option[ApproveFunction.Result] = ???
  //  }
  //
  //  object TransferFromFunction {
  //    val name = "transferFrom"
  //
  //    def apply: TransferFromFunction = new TransferFromFunction()
  //
  //    case class Parms(owner: String)
  //    case class Result()
  //  }
  //
  //    class TransferFromFunction extends AbiFunction[TransferFromFunction.Parms, TransferFromFunction.Result] {
  //
  //      val entry = abi.findFunction(searchByName(TransferFromFunction.name))
  //
  //      def pack(t: TransferFromFunction.Parms): Array[Byte] = ???
  //
  //      def unpackInput(data: Array[Byte]): Option[TransferFromFunction.Parms] = ???
  //
  //      def unpackResult(data: Array[Byte]): Option[TransferFromFunction.Result] = ???
  //    }


  //
  //  object ApprovalEvent {
  //
  //    val name = "Approval"
  //
  //    def apply: ApprovalEvent = new ApprovalEvent()
  //
  //    case class Result()
  //
  //  }
  //    class ApprovalEvent extends AbiEvent[ApprovalEvent.Result] {
  //      val entry = abi.findEvent(searchByName("Approval"))
  //      def unpack(log: TransactionLog): Option[ApprovalEvent.Result] = ???
  //    }

}
