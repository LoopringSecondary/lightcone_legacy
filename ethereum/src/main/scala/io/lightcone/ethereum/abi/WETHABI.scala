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

package io.lightcone.ethereum.abi

import org.ethereum.solidity.Abi
import org.web3j.utils.Numeric

import scala.annotation.meta.field
import scala.io.Source

object WETHAbi {

  val abijsonstr = Source.fromResource("version20/WETH.abi").mkString

  def apply(): WETHAbi = new WETHAbi(abijsonstr)

  def apply(jsonstr: String) = new WETHAbi(jsonstr)
}

class WETHAbi(abiJson: String) extends ERC20Abi(abiJson) {

  val deposit = DepositFunction(
    abi.findFunction(searchByName(DepositFunction.name))
  )

  val withdraw = WithdrawFunction(
    abi.findFunction(searchByName(WithdrawFunction.name))
  )

  val depositEvent = DepositEvent(
    abi.findEvent(searchByName(DepositEvent.name))
  )

  val withdrawalEvent = WithdrawalEvent(
    abi.findEvent(searchByName(WithdrawalEvent.name))
  )

  override def unpackEvent(
      data: String,
      topics: Array[String]
    ): Option[Any] = {
    try {
      val event: Abi.Event = abi.findEvent(
        searchBySignature(
          Numeric.hexStringToByteArray(topics.headOption.getOrElse(""))
        )
      )
      event match {
        case _: Abi.Event =>
          event.name match {
            case ApprovalEvent.name =>
              approvalEvent.unpack(data, topics)
            case TransferEvent.name =>
              transferEvent.unpack(data, topics)
            case DepositEvent.name =>
              depositEvent.unpack(data, topics)
            case WithdrawalEvent.name =>
              withdrawalEvent.unpack(data, topics)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

  override def unpackFunctionInput(data: String): Option[Any] = {
    try {
      val funSig =
        Numeric.hexStringToByteArray(
          Numeric.cleanHexPrefix(data).substring(0, 8)
        )
      val func = abi.findFunction(searchBySignature(funSig))
      func match {
        case _: Abi.Function =>
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
            case DepositFunction.name =>
              deposit.unpackInput(data)
            case WithdrawFunction.name =>
              withdraw.unpackInput(data)
            case _ => None
          }
        case _ => None
      }
    } catch {
      case _: Throwable => None
    }
  }

}

object DepositFunction {

  case class Parms()

  val name = "deposit"

  case class Result()

  def apply(entry: Abi.Function): DepositFunction = new DepositFunction(entry)
}

class DepositFunction(val entry: Abi.Function)
    extends AbiFunction[DepositFunction.Parms, DepositFunction.Result]

object WithdrawFunction {

  case class Parms(@(ContractAnnotation @field)("wad", 0) wad: BigInt)

  val name = "withdraw"

  case class Result()

  def apply(entry: Abi.Function): WithdrawFunction =
    new WithdrawFunction(entry)
}

class WithdrawFunction(val entry: Abi.Function)
    extends AbiFunction[WithdrawFunction.Parms, WithdrawFunction.Result]

object DepositEvent {

  case class Parms()

  val name = "Deposit"

  case class Result(
      @(ContractAnnotation @field)("dst", 0) dst: String,
      @(ContractAnnotation @field)("wad", 1) wad: BigInt)

  def apply(entry: Abi.Event): DepositEvent = new DepositEvent(entry)
}

class DepositEvent(val entry: Abi.Event) extends AbiEvent[DepositEvent.Result]

object WithdrawalEvent {

  case class Prams()

  val name = "Withdrawal"

  case class Result(
      @(ContractAnnotation @field)("src", 0) src: String,
      @(ContractAnnotation @field)("wad", 1) wad: BigInt)

  def apply(entry: Abi.Event): WithdrawalEvent = new WithdrawalEvent(entry)
}

class WithdrawalEvent(val entry: Abi.Event)
    extends AbiEvent[WithdrawalEvent.Result]
