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
  TransactionReceipt
}
import org.web3j.utils.Numeric

abstract class TransactionExtractor {

  def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent]

  def Transaction2Event(tx: Transaction): TransactionEvent = {
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

case class CommonTransactionExtractor() extends TransactionExtractor {

  def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs.map(
        tx ⇒
          Transaction2Event(tx._1).copy(
            eventType = TransactionEvent.Type.COMMON
          )
      )
    } else {
      Seq.empty
    }
  }
}

case class EthTransactionExtractor() extends TransactionExtractor {

  def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs
        .filter(tx ⇒ Numeric.toBigInt(tx._2.get.status).intValue() == 1)
        .flatMap(item ⇒ {
          val (tx, receiptOpt) = item
          var events = Seq.empty[TransactionEvent]
          if (receiptOpt.get.logs.isEmpty) {
            events = events.+:(
              Transaction2Event(tx).copy(
                eventType = TransactionEvent.Type.ETH,
                sender = tx.from,
                receiver = tx.to
              )
            )
          } else {
            events = events.++:(
              receiptOpt.get.logs
                .map(log ⇒ {
                  wethAbi.unpackEvent(log.data, log.topics.toArray) match {
                    case Some(withdraw: WithdrawalEvent.Result) ⇒
                      Some(
                        Transaction2Event(tx).copy(
                          eventType = TransactionEvent.Type.ETH,
                          sender = log.address,
                          receiver = withdraw.src,
                          value = Numeric
                            .toHexStringWithPrefix(withdraw.wad.bigInteger)
                        )
                      )
                    case Some(deposit: DepositEvent.Result) ⇒
                      Some(
                        Transaction2Event(tx).copy(
                          eventType = TransactionEvent.Type.ETH,
                          sender = deposit.dst,
                          receiver = log.address,
                          value = Numeric
                            .toHexStringWithPrefix(deposit.wad.bigInteger)
                        )
                      )
                    case _ ⇒ None
                  }
                })
                .filter(_.nonEmpty)
                .map(_.get)
            )
          }
          events
        })
    } else {
      Seq.empty
    }
  }
}

case class TokenTransactionExtractor()(implicit protocolAddress: Address)
    extends TransactionExtractor {

  def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs
        .filterNot(tx ⇒ Address(tx._1.to).equals(protocolAddress))
        .flatMap(item ⇒ {
          val (tx, receiptOpt) = item
          receiptOpt.get.logs
            .map(log ⇒ {
              wethAbi.unpackEvent(log.data, log.topics.toArray) match {
                case Some(transfer: TransferEvent.Result) ⇒
                  Some(
                    Transaction2Event(tx).copy(
                      eventType = TransactionEvent.Type.TOKEN,
                      receiver = transfer.receiver,
                      sender = transfer.from,
                      token = log.address,
                      value = Numeric
                        .toHexStringWithPrefix(transfer.amount.bigInteger)
                    )
                  )
                case Some(withdraw: WithdrawalEvent.Result) ⇒
                  Some(
                    Transaction2Event(tx).copy(
                      eventType = TransactionEvent.Type.TOKEN,
                      receiver = log.address,
                      sender = withdraw.src,
                      token = log.address,
                      value = Numeric
                        .toHexStringWithPrefix(withdraw.wad.bigInteger)
                    )
                  )
                case Some(deposit: DepositEvent.Result) ⇒
                  Some(
                    Transaction2Event(tx).copy(
                      eventType = TransactionEvent.Type.TOKEN,
                      receiver = deposit.dst,
                      sender = log.address,
                      token = log.address,
                      value = Numeric
                        .toHexStringWithPrefix(deposit.wad.bigInteger)
                    )
                  )
                case _ ⇒ None
              }
            })
            .filter(_.nonEmpty)
            .map(_.get)
        })
    } else {
      Seq.empty
    }
  }

}

case class TradeTransactionExtractor()(implicit protocolAddress: Address)
    extends TransactionExtractor {

  def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs
        .filter(tx ⇒ Address(tx._1.to).equals(protocolAddress))
        .flatMap(item ⇒ {
          val (tx, receiptOpt) = item
          receiptOpt.get.logs
            .map(log ⇒ {
              wethAbi.unpackEvent(log.data, log.topics.toArray) match {
                case Some(transfer: TransferEvent.Result) ⇒
                  Some(
                    Transaction2Event(tx).copy(
                      eventType = TransactionEvent.Type.TRADE,
                      receiver = transfer.receiver,
                      sender = transfer.from,
                      token = log.address,
                      value = Numeric
                        .toHexStringWithPrefix(transfer.amount.bigInteger)
                    )
                  )
                case _ ⇒ None
              }
            })
            .filter(_.nonEmpty)
            .map(_.get)
        })
    } else {
      Seq.empty
    }
  }
}

case class LoopringTransactionExtractor() extends TransactionExtractor {
  override def extract(
      txs: Seq[(Transaction, Option[TransactionReceipt])]
    ): Seq[TransactionEvent] = {
    if (txs.forall(_._2.nonEmpty)) {
      txs.flatMap(item ⇒ {
        val (tx, receiptOpt) = item
        receiptOpt.get.logs
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
                  Transaction2Event(tx).copy(
                    eventType = TransactionEvent.Type.LOOPRING
                  )
                )
              case _ ⇒
                None
            }
          })
          .filter(_.nonEmpty)
          .map(_.get)
      })
    } else {
      Seq.empty
    }
  }
}
