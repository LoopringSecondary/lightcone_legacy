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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.proto.{
  AddressBalanceUpdated,
  Transaction,
  TransactionReceipt
}
import org.web3j.utils.Numeric

import scala.collection.mutable.ListBuffer

class BalanceChangedAddressExtractor()
    extends EventExtractor[AddressBalanceUpdated] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[AddressBalanceUpdated] = {
    val balanceAddresses = ListBuffer(
      AddressBalanceUpdated(tx.from, Address.ZERO.toString())
    )
    if (isSucceed(receipt.status) && receipt.logs.isEmpty &&
        BigInt(Numeric.toBigInt(tx.value)) > 0) {
      balanceAddresses.append(
        AddressBalanceUpdated(tx.to, Address.ZERO.toString())
      )
    }
    receipt.logs.foreach(log => {
      wethAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(transfer: TransferEvent.Result) =>
          balanceAddresses.append(
            AddressBalanceUpdated(transfer.from, log.address),
            AddressBalanceUpdated(transfer.receiver, log.address)
          )
        case Some(deposit: DepositEvent.Result) =>
          balanceAddresses.append(
            AddressBalanceUpdated(deposit.dst, log.address)
          )
        case Some(withdrawal: WithdrawalEvent.Result) =>
          balanceAddresses.append(
            AddressBalanceUpdated(withdrawal.src, log.address)
          )
        case _ =>
      }
    })

    balanceAddresses.distinct
  }
}
