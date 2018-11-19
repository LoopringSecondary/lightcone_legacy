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

import org.loopring.lightcone.lib.data._

class WethABI(abiJson: String) extends ERC20ABI(abiJson) {

  val FN_DEPOSIT = "deposit"
  val FN_WITHDRAWAL = "withdraw"
  val EN_DEPOSIT = "Deposit"
  val EN_WITHDRAWAL = "Withdrawal"

  override def decodeAndAssemble(tx: Transaction): Option[Any] = {
    val result = decode(tx.input)
    result.name match {
      case FN_TRANSFER      ⇒ Some(assembleTransferFunction(result.list, tx.from))
      case FN_TRANSFER_FROM ⇒ Some(assembleTransferFromFunction(result.list))
      case FN_APPROVE       ⇒ Some(assembleApproveFunction(result.list, tx.from))
      case FN_DEPOSIT       ⇒ Some(assembleDepositFunction(tx.from, tx.value))
      case FN_WITHDRAWAL    ⇒ Some(assembleWithdrawalFunction(result.list, tx.from))
    }
  }

  override def decodeAndAssemble(tx: Transaction, log: TransactionLog): Option[Any] = {
    val result = decode(log)
    result.name match {
      case EN_TRANSFER   ⇒ Some(assembleTransferEvent(result.list))
      case EN_APPROVAL   ⇒ Some(assembleApprovalEvent(result.list))
      case EN_DEPOSIT    ⇒ Some(assembleDepositEvent(result.list))
      case EN_WITHDRAWAL ⇒ Some(assembleWithdrawalEvent(result.list))
    }
  }

  private[lib] def assembleDepositFunction(from: String, value: BigInt) =
    Deposit(from, value)

  private[lib] def assembleWithdrawalFunction(list: Seq[Any], from: String) = {
    assert(list.length == 1, "withdrawal function list length invalid")

    Withdrawal(
      owner = from,
      amount = scalaAny2Bigint(list(0))
    )
  }

  private[lib] def assembleDepositEvent(list: Seq[Any]): Deposit = {
    assert(list.length == 2, "deposit function list length invalid")

    Deposit(
      owner = scalaAny2Hex(list(0)),
      amount = scalaAny2Bigint(list(1))
    )
  }

  // event  Withdrawal(address indexed src, uint wad);
  private[lib] def assembleWithdrawalEvent(list: Seq[Any]): Withdrawal = {
    assert(list.length == 2, "withdrawal event list length invalid")

    Withdrawal(
      owner = scalaAny2Hex(list(0)),
      amount = scalaAny2Bigint(list(1))
    )
  }
}
