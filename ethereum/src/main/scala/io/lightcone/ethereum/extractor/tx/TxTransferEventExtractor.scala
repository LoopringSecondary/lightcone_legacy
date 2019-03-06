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

package io.lightcone.ethereum.extractor.tx

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core.MetadataManager
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.extractor._
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.lib._
import io.lightcone.relayer.data._
import io.lightcone.ethereum.event.{
  EventHeader,
  TransferEvent => PTransferEvent
}

import scala.concurrent._

final class TxTransferEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val metadataManager: MetadataManager,
    val config: Config)
    extends EventExtractor[TransactionData, AnyRef] {

  val wethAddress =
    metadataManager.getTokenWithSymbol("weth").get.getMetadata.address

  val protocolAddress =
    Address.normalize(config.getString("loopring_protocol.protocol-address"))

  def extractEvents(txData: TransactionData): Future[Seq[AnyRef]] = Future {
    val transferEvents = extractTransferEvents(txData)
    transferEvents.filterNot { event =>
      event.getHeader.txTo == protocolAddress || event.owner == wethAddress
    }.map(extractActivity)
  }

  //从TransferEvent抽取Transfer Activity,Wrap,Unwrap等Activity
  def extractActivity(event: PTransferEvent): Activity = {
    Activity(
      owner = event.owner,
      block = event.getHeader.blockHeader.map(_.height).getOrElse(-1L),
      txHash = event.getHeader.txHash,
      activityType = getActivityType(event),
      timestamp = event.getHeader.blockHeader.map(_.timestamp).getOrElse(0L),
      token = event.token,
      detail = getActivityDetail(event)
    )
  }

  def extractTransferEvents(txData: TransactionData): Seq[PTransferEvent] = {
    val events = txData.receiptAndHeaderOpt match {
      case Some((receipt, header)) if header.txStatus.isTxStatusSuccess =>
        extractFromReceipt(receipt, Some(header))

      case Some((_, header)) if header.txStatus.isTxStatusFailed =>
        extractFromTxInput(txData.tx, Some(header))

      case _ =>
        val tx = txData.tx
        val eventHeader = EventHeader(
          txFrom = Address.normalize(tx.from),
          txHash = tx.hash,
          txTo = Address.normalize(tx.to),
          txStatus = TxStatus.TX_STATUS_PENDING,
          txValue = Some(NumericConversion.toAmount(tx.value))
        )
        extractFromTxInput(tx, Some(eventHeader))
    }

    events.flatMap(
      event => Seq(event.withOwner(event.from), event.withOwner(event.to))
    )
  }

  //抽取成功的事件
  def extractFromReceipt(
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): Seq[PTransferEvent] = {
    val txValue = NumericConversion.toBigInt(header.get.getTxValue)
    val events = receipt.logs.flatMap { log =>
      wethAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(transfer: TransferEvent.Result) =>
          Seq(
            PTransferEvent(
              header = header,
              from = Address.normalize(transfer.from),
              to = Address.normalize(transfer.receiver),
              token = Address.normalize(log.address),
              amount = Some(NumericConversion.toAmount(transfer.amount))
            )
          )
        case Some(withdraw: WithdrawalEvent.Result) =>
          Seq(
            PTransferEvent(
              header = header,
              from = Address.normalize(withdraw.src),
              to = Address.normalize(log.address),
              token = Address.normalize(log.address),
              amount = Some(NumericConversion.toAmount(withdraw.wad))
            ),
            PTransferEvent(
              header,
              from = Address.normalize(log.address),
              to = Address.normalize(withdraw.src),
              token = Address.ZERO.toString(),
              amount = Some(NumericConversion.toAmount(withdraw.wad))
            )
          )
        case Some(deposit: DepositEvent.Result) =>
          Seq(
            PTransferEvent(
              header = header,
              from = Address.normalize(log.address),
              to = Address.normalize(deposit.dst),
              token = Address.normalize(log.address),
              amount = Some(NumericConversion.toAmount(deposit.wad))
            )
          )
        case _ => Seq.empty
      }
    }

    if (txValue > 0) {
      events.+:(
        PTransferEvent(
          header = header,
          from = Address.normalize(receipt.from),
          to = Address.normalize(receipt.to),
          token = Address.ZERO.toString(),
          amount = Some(NumericConversion.toAmount(txValue))
        )
      )
    } else events
  }

  //抽取失败的事件或者pending的tx的事件
  def extractFromTxInput(
      tx: Transaction,
      header: Option[EventHeader]
    ): Seq[PTransferEvent] = {
    val txValue = NumericConversion.toBigInt(tx.value)
    wethAbi.unpackFunctionInput(tx.input) match {
      case Some(transfer: TransferFunction.Parms) =>
        Seq(
          PTransferEvent(
            header,
            from = Address.normalize(tx.from),
            to = Address.normalize(transfer.to),
            token = Address.normalize(tx.to),
            amount = Some(NumericConversion.toAmount(transfer.amount))
          )
        )
      case Some(transferFrom: TransferFromFunction.Parms) =>
        Seq(
          PTransferEvent(
            header = header,
            from = Address.normalize(transferFrom.txFrom),
            to = Address.normalize(transferFrom.to),
            token = Address.normalize(tx.to),
            amount = Some(NumericConversion.toAmount(transferFrom.amount))
          )
        )
      case Some(_: DepositFunction.Parms) =>
        Seq(
          PTransferEvent(
            header = header,
            from = Address.normalize(tx.to),
            to = Address.normalize(tx.from),
            token = Address.normalize(tx.to),
            amount = Some(NumericConversion.toAmount(txValue))
          ),
          PTransferEvent(
            header = header,
            from = Address.normalize(tx.from),
            to = Address.normalize(tx.to),
            token = Address.ZERO.toString(),
            amount = Some(NumericConversion.toAmount(txValue))
          )
        )
      case Some(withdraw: WithdrawFunction.Parms) =>
        Seq(
          PTransferEvent(
            header = header,
            from = Address.normalize(tx.from),
            to = Address.normalize(tx.to),
            token = Address.normalize(tx.to),
            amount = Some(NumericConversion.toAmount(withdraw.wad))
          ),
          PTransferEvent(
            header = header,
            from = Address.normalize(tx.to),
            to = Address.normalize(tx.from),
            token = Address.ZERO.toString(),
            amount = Some(NumericConversion.toAmount(withdraw.wad))
          )
        )
      case _ =>
        if (txValue > 0) {
          val ethTransfer = PTransferEvent(
            header = header,
            from = Address.normalize(tx.from),
            to = Address.normalize(tx.to),
            token = Address.ZERO.toString(),
            amount = Some(NumericConversion.toAmount(txValue))
          )
          if (Address.normalize(tx.to) == wethAddress) {
            Seq(
              PTransferEvent(
                header = header,
                from = Address.normalize(tx.to),
                to = Address.normalize(tx.from),
                token = Address.normalize(tx.to),
                amount = Some(NumericConversion.toAmount(txValue))
              ),
              ethTransfer
            )
          } else Seq(ethTransfer)
        } else Seq.empty
    }

  }

  def getActivityType(event: PTransferEvent): Activity.ActivityType = {
    if (event.to == wethAddress && event.token == Address.ZERO.toString())
      Activity.ActivityType.ETHER_WRAP
    else if (event.to == wethAddress && event.token == wethAddress)
      Activity.ActivityType.ETHER_UNWRAP
    else if (event.from == wethAddress && event.token == Address.ZERO
               .toString())
      Activity.ActivityType.ETHER_UNWRAP
    else if (event.from == wethAddress && event.token == wethAddress)
      Activity.ActivityType.ETHER_WRAP
    else if (event.from == event.owner && event.token == Address.ZERO
               .toString())
      Activity.ActivityType.ETHER_TRANSFER_OUT
    else if (event.from == event.owner && event.token != Address.ZERO
               .toString())
      Activity.ActivityType.TOKEN_TRANSFER_OUT
    else if (event.token == Address.ZERO.toString())
      Activity.ActivityType.ETHER_TRANSFER_IN
    else
      Activity.ActivityType.TOKEN_TRANSFER_IN
  }

  def getActivityDetail(event: PTransferEvent): Activity.Detail = {

    if (event.to == wethAddress || event.from == wethAddress) {
      Activity.Detail.EtherConversion(
        Activity.EtherConversion(amount = event.amount)
      )
    } else if (event.token == Address.ZERO.toString()) {
      Activity.Detail.EtherTransfer(
        Activity.EtherTransfer(address = event.to, amount = event.amount)
      )
    } else {
      Activity.Detail.TokenTransfer(
        Activity.TokenTransfer(address = event.to, amount = event.amount)
      )
    }
  }
}
