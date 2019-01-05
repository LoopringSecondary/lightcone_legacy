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
  Transaction,
  TransactionEvent,
  TransactionReceipt,
  TxStatus
}
import org.web3j.utils.Numeric

trait TransactionExtractor extends Extractor {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent]

  def txToEvent(tx: Transaction): TransactionEvent = {
    TransactionEvent(
      hash = tx.hash,
      nonce = tx.nonce,
      blockHash = tx.blockHash,
      blockNumber = tx.blockNumber,
      transactionIndex = tx.transactionIndex,
      from = tx.from,
      to = tx.to,
      value = tx.value,
      gasPrice = tx.gasPrice,
      gas = tx.gas,
      input = tx.input
    )
  }
}

class EthTransactionExtractor() extends TransactionExtractor {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    extractSucceedTransactions(tx, receipt) ++
      extractFailedTransactions(tx, receipt
    )
  }

  def extractSucceedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    if (BigInt(Numeric.toBigInt(tx.value)) > 0) {
      Seq(
        txToEvent(tx).copy(
          eventType = TransactionEvent.Type.ETH,
          sender = tx.from,
          receiver = tx.to
        )
      )
    } else {
      receipt.logs
        .map(log ⇒ {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(withdraw: WithdrawalEvent.Result) ⇒
              Some(
                txToEvent(tx).copy(
                  eventType = TransactionEvent.Type.ETH,
                  sender = log.address,
                  receiver = withdraw.src,
                  value = Numeric
                    .toHexStringWithPrefix(withdraw.wad.bigInteger)
                )
              )
            case _ ⇒ None
          }
        })
        .filter(_.nonEmpty)
        .map(_.get)
    }
  }

  def extractFailedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    if (BigInt(Numeric.toBigInt(tx.value)) > 0) {
      Seq(
        txToEvent(tx).copy(
          eventType = TransactionEvent.Type.ETH,
          receiver = tx.to,
          sender = tx.from,
          status = TxStatus.TX_STATUS_FAILED
        )
      )
    } else {
      wethAbi.unpackFunctionInput(tx.input) match {
        case Some(withdraw: WithdrawFunction.Parms) ⇒
          Seq(
            txToEvent(tx).copy(
              eventType = TransactionEvent.Type.ETH,
              receiver = tx.from,
              sender = tx.to,
              value = Numeric.toHexStringWithPrefix(withdraw.wad.bigInteger),
              status = TxStatus.TX_STATUS_FAILED
            )
          )
        case _ ⇒
          Seq.empty
      }
    }
  }
}

 class TokenTransactionExtractor()(implicit protocolAddress: Address)
    extends TransactionExtractor {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    extractSucceedTransactions(tx, receipt) ++
      extractFailedTransactions(tx, receipt)
  }

  def extractSucceedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    receipt.logs
      .map(log ⇒ {
        wethAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(transfer: TransferEvent.Result) ⇒
            Some(
              txToEvent(tx).copy(
                eventType = TransactionEvent.Type.TOKEN,
                receiver = transfer.receiver,
                sender = transfer.from,
                token = log.address,
                value = Numeric
                  .toHexStringWithPrefix(transfer.amount.bigInteger),
                status = TxStatus.TX_STATUS_SUCCESS
              )
            )
          case Some(withdraw: WithdrawalEvent.Result) ⇒
            Some(
              txToEvent(tx).copy(
                eventType = TransactionEvent.Type.TOKEN,
                receiver = log.address,
                sender = withdraw.src,
                token = log.address,
                value = Numeric
                  .toHexStringWithPrefix(withdraw.wad.bigInteger),
                status = TxStatus.TX_STATUS_SUCCESS
              )
            )
          case Some(deposit: DepositEvent.Result) ⇒
            Some(
              txToEvent(tx).copy(
                eventType = TransactionEvent.Type.TOKEN,
                receiver = deposit.dst,
                sender = log.address,
                token = log.address,
                value = Numeric
                  .toHexStringWithPrefix(deposit.wad.bigInteger),
                status = TxStatus.TX_STATUS_SUCCESS
              )
            )
          case _ ⇒ None
        }
      })
      .filter(_.nonEmpty)
      .map(_.get)
  }

  def extractFailedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    wethAbi.unpackFunctionInput(tx.input) match {
      case Some(transfer: TransferFunction.Parms) ⇒
        Seq(
          txToEvent(tx).copy(
            eventType = TransactionEvent.Type.TOKEN,
            receiver = transfer.to,
            sender = tx.from,
            token = tx.to,
            value = Numeric.toHexStringWithPrefix(transfer.amount.bigInteger),
            status = TxStatus.TX_STATUS_FAILED
          )
        )
      case Some(transferFrom: TransferFromFunction.Parms) ⇒
        Seq(
          txToEvent(tx).copy(
            eventType = TransactionEvent.Type.TOKEN,
            receiver = transferFrom.to,
            sender = transferFrom.txFrom,
            token = tx.to,
            value =
              Numeric.toHexStringWithPrefix(transferFrom.amount.bigInteger),
            status = TxStatus.TX_STATUS_FAILED
          )
        )
      case Some(DepositFunction.Parms) ⇒
        Seq(
          txToEvent(tx).copy(
            eventType = TransactionEvent.Type.TOKEN,
            receiver = tx.from,
            sender = tx.to,
            token = tx.to,
            status = TxStatus.TX_STATUS_FAILED
          )
        )
      case Some(withdraw: WithdrawFunction.Parms) ⇒
        Seq(
          txToEvent(tx).copy(
            eventType = TransactionEvent.Type.TOKEN,
            receiver = tx.to,
            sender = tx.from,
            token = tx.to,
            value = Numeric.toHexStringWithPrefix(withdraw.wad.bigInteger),
            status = TxStatus.TX_STATUS_FAILED
          )
        )
      case _ ⇒ Seq.empty
    }
  }
}

 class TradeTransactionExtractor()(implicit protocolAddress: Address)
    extends TransactionExtractor {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    receipt.logs
      .map(log ⇒ {
        wethAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(transfer: TransferEvent.Result) ⇒
            Some(
              txToEvent(tx).copy(
                eventType = TransactionEvent.Type.TRADE,
                receiver = transfer.receiver,
                sender = transfer.from,
                token = log.address,
                value = Numeric
                  .toHexStringWithPrefix(transfer.amount.bigInteger),
                status = TxStatus.TX_STATUS_SUCCESS
              )
            )
          case _ ⇒ None
        }
      })
      .filterNot(_.nonEmpty)
      .map(_.get)
  }
}

 class LoopringTransactionExtractor() extends TransactionExtractor {
  override def extract(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    extractSucceedTransactions(tx, receipt) ++
      extractFailedTransactions(tx, receipt)
  }

  private def extractSucceedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    receipt.logs
      .map(log ⇒ {
        loopringProtocolAbi
          .unpackEvent(log.data, log.topics.toArray) match {
          case Some(
              OrderSubmittedEvent.Result | AllOrdersCancelledEvent.Result |
              AllOrdersCancelledByBrokerEvent.Result |
              AllOrdersCancelledForTradingPairEvent.Result |
              AllOrdersCancelledForTradingPairByBrokerEvent.Result
              ) ⇒
            Some(
              txToEvent(tx).copy(
                eventType = TransactionEvent.Type.LOOPRING,
                status = TxStatus.TX_STATUS_SUCCESS
              )
            )
          case _ ⇒
            None
        }
      })
      .filter(_.nonEmpty)
      .map(_.get)
  }

  private def extractFailedTransactions(
      tx: Transaction,
      receipt: TransactionReceipt
    ): Seq[TransactionEvent] = {
    loopringProtocolAbi.unpackFunctionInput(tx.input) match {
      case Some(_) ⇒
        Seq(
          txToEvent(tx).copy(
            eventType = TransactionEvent.Type.LOOPRING,
            status = TxStatus.TX_STATUS_FAILED
          )
        )
      case _ ⇒
        Seq.empty
    }
  }
}
