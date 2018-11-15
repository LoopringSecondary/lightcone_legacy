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

package org.loopring.lightcone.lib

case class Transfer(sender: String, receiver: String, amount: BigInt)
case class Approve(owner: String, spender: String, amount: BigInt)

class ERC20ABI(abiJson: String) extends AbiWrap(abiJson) {

  val FN_TRANSFER = "transfer"
  val FN_TRANSFER_FROM = "transferFrom"
  val FN_APPROVE = "approve"
  val EN_TRANSFER = "Transfer"
  val EN_APPROVAL = "Approval"

  def decodeAndAssemble(tx: Transaction): Option[Any] = {
    val result = decode(tx.input)
    result.name match {
      case FN_TRANSFER ⇒ Some(assembleTransferFunction(result.list, tx.from))
      case FN_TRANSFER_FROM ⇒ Some(assembleTransferFromFunction(result.list))
      case FN_APPROVE ⇒ Some(assembleApproveFunction(result.list, tx.from))
    }
  }

  def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    result.name match {
      case EN_TRANSFER ⇒ Some(assembleTransferEvent(result.list))
      case EN_APPROVAL ⇒ Some(assembleApprovalEvent(result.list))
    }
  }

  private[lib] def assembleTransferFunction(list: Seq[Any], from: String): Transfer = {
    assert(list.length == 2, "length of transfer function invalid")

    Transfer(
      sender = from,
      receiver = scalaAny2Hex(list(0)),
      amount = scalaAny2Bigint(list(1)))
  }

  private[lib] def assembleTransferFromFunction(list: Seq[Any]): Transfer = {
    assert(list.length == 3, "length of transfer from function invalid")

    Transfer(
      sender = scalaAny2Hex(list(0)),
      receiver = scalaAny2Hex(list(1)),
      amount = scalaAny2Bigint(list(2)))
  }

  private[lib] def assembleTransferEvent(list: Seq[Any]): Transfer = {
    assert(list.length == 3, "length of transfer event invalid")

    Transfer(
      sender = scalaAny2Hex(list(0)),
      receiver = scalaAny2Hex(list(1)),
      amount = scalaAny2Bigint(list(2)))
  }

  private[lib] def assembleApproveFunction(list: Seq[Any], from: String): Approve = {
    assert(list.length == 2, "length of approve function invalid")

    Approve(
      owner = from,
      spender = scalaAny2Hex(list(0)),
      amount = scalaAny2Bigint(list(1)))
  }

  private[lib] def assembleApprovalEvent(list: Seq[Any]): Approve = {
    assert(list.length == 3, "length of approve event invalid")

    Approve(
      owner = scalaAny2Hex(list(0)),
      spender = scalaAny2Hex(list(1)),
      amount = scalaAny2Bigint(list(2)))
  }

}
