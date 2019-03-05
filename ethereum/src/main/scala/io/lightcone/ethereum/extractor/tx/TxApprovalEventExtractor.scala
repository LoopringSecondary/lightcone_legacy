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
import io.lightcone.ethereum.TxStatus
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.extractor._
import io.lightcone.lib._
import io.lightcone.relayer.data._
import io.lightcone.ethereum.event.{
  EventHeader,
  ApprovalEvent => PApprovalEvent
}
import io.lightcone.ethereum.persistence.Activity

import scala.concurrent.{ExecutionContext, Future}

class TxApprovalEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends EventExtractor[TransactionData, AnyRef] {

  val delegateAddress =
    Address.normalize(config.getString("loopring_protocol.delegate-address"))

  val protocolAddress =
    Address.normalize(config.getString("loopring_protocol.protocol-address"))

  def extractEvents(source: TransactionData): Future[Seq[AnyRef]] = Future {
    val events = extractApproveEvents(source)
    events
      .filterNot(event => event.getHeader.txTo == protocolAddress)
      .map(extractTokenAuthActivity)
  }

  // 从ApprovalEvent中抽取Token Auth Activity
  def extractTokenAuthActivity(event: PApprovalEvent): Activity = {
    Activity(
      owner = event.owner,
      block = event.getHeader.blockHeader.map(_.height).getOrElse(-1L),
      txHash = event.getHeader.txHash,
      activityType = Activity.ActivityType.TOKEN_AUTH,
      timestamp = event.getHeader.blockHeader.map(_.timestamp).getOrElse(0L),
      token = event.token,
      detail = Activity.Detail.TokenAuth(
        Activity.TokenAuth(
          token = event.token,
          target = event.spender,
          amount = event.amount
        )
      )
    )
  }

  def extractApproveEvents(txData: TransactionData) = {
    txData.receiptAndHeaderOpt match {
      case Some((receipt, header)) if header.txStatus.isTxStatusSuccess =>
        (extractFromReceipt(receipt, Some(header))
          ++ extractFromTxInput(txData.tx, Some(header))).distinct

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
  }

  //提取由于交易造成的授权变化以及成功的授权事件
  def extractFromReceipt(
      receipt: TransactionReceipt,
      header: Option[EventHeader]
    ): Seq[PApprovalEvent] = {
    receipt.logs.map { log =>
      erc20Abi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(transfer: TransferEvent.Result)
            if protocolAddress == Address.normalize(receipt.to) =>
          Some(
            PApprovalEvent(
              header = header,
              owner = Address.normalize(transfer.from),
              spender = delegateAddress,
              token = Address.normalize(log.address),
              amount = Some(NumericConversion.toAmount(transfer.amount))
            )
          )
        case Some(approval: ApprovalEvent.Result) =>
          Some(
            PApprovalEvent(
              header = header,
              owner = Address.normalize(approval.owner),
              spender = Address.normalize(approval.spender),
              token = Address.normalize(log.address),
              amount = Some(NumericConversion.toAmount(approval.amount))
            )
          )
        case _ => None
      }
    }.filter(_.isDefined).map(_.get)
  }

  //失败或者pending的tx 使用该方法进行提取,由于BNB没有授权事件，此类无授权事件的token也应该采用该方法进行提取
  def extractFromTxInput(
      tx: Transaction,
      header: Option[EventHeader]
    ): Seq[PApprovalEvent] =
    erc20Abi.unpackFunctionInput(tx.input) match {
      case Some(param: ApproveFunction.Parms) =>
        Seq(
          PApprovalEvent(
            header = header,
            owner = Address.normalize(tx.from),
            spender = Address.normalize(param.spender),
            token = Address.normalize(tx.to),
            amount = Some(NumericConversion.toAmount(param.amount))
          )
        )
      case _ => Seq.empty
    }
}
