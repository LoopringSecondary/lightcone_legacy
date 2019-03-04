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

package io.lightcone.ethereum.extractor

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.persistence.Activity
import io.lightcone.ethereum.persistence.Activity.ActivityType
import io.lightcone.lib._
import io.lightcone.relayer.data.{Transaction, TransactionReceipt}

import scala.concurrent._

class TxCutoffEventExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends EventExtractor[TransactionData, Any] {

  val orderCancelAddress = Address(
    config.getString("loopring_protocol.order-cancel-address")
  ).toString()

  def extractEvents(txdata: TransactionData) = Future {
    val (cutoffEvents, blockHeader) = if (!txdata.tx.to.equalsIgnoreCase(orderCancelAddress)) {
      (Seq.empty, BlockHeader())
    } else {
      txdata.receiptAndHeaderOpt match {
        case Some((receipt, eventHeader)) =>
          (extractBlockedEvents(txdata.tx, receipt, eventHeader), eventHeader.getBlockHeader)
        case None =>
          (extractPendingEvents(txdata.tx), BlockHeader())
      }
    }
    val activities = cutoffEvents.map {
      case evt: CutoffEvent =>
        val detail = Activity.OrderCancellation(
          cutoff = evt.cutoff,
          broker = evt.broker,
          marketPair = evt.marketHash //TODO(hongyu):Markethash与marketPair如何使用
        )
        Seq(
          Activity(
            owner = evt.owner,
            block = blockHeader.height,
            activityType = ActivityType.ORDER_CANCEL,
            timestamp = blockHeader.timestamp,
//            fiatValue = 0.0, //TODO:(hongyu):确定法币金额是现在计算还是返回时计算
            token = Address.ZERO.toString(), //TODO(hongyu):取消订单按照ETH
            detail = Activity.Detail(detail)
          )
        )
      case evt: OrdersCancelledOnChainEvent =>
        val detail = Activity.OrderCancellation(
          broker = evt.broker,
          orderIds = evt.orderHashes
        )
        Seq(
          Activity(
            owner = evt.owner,
            block = blockHeader.height,
            activityType = ActivityType.ORDER_CANCEL,
            timestamp = blockHeader.timestamp,
            //            fiatValue = 0.0, //TODO:(hongyu):确定法币金额是现在计算还是返回时计算
            token = Address.ZERO.toString(), //TODO(hongyu):取消订单按照ETH
            detail = Activity.Detail(detail)
          )
        )
      case _ => Seq.empty
    }

    cutoffEvents ++ activities.flatten
  }

  def extractBlockedEvents(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Seq[Any] = {

    receipt.logs.zipWithIndex.map {
      case (log, index) =>
        loopringProtocolAbi
          .unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: AllOrdersCancelledEvent.Result) =>
            Some(
              CutoffEvent(
                header = Some(eventHeader),
                cutoff = event._cutoff.longValue,
                broker = Address.normalize(event._broker),
                owner = Address.normalize(event._broker)
              )
            )
          case Some(event: AllOrdersCancelledByBrokerEvent.Result) =>
            Some(
              CutoffEvent(
                header = Some(eventHeader),
                cutoff = event._cutoff.longValue,
                broker = Address.normalize(event._broker),
                owner = Address.normalize(event._owner)
              )
            )
          case Some(
              event: AllOrdersCancelledForTradingPairByBrokerEvent.Result
              ) =>
            Some(
              CutoffEvent(
                header = Some(eventHeader),
                cutoff = event._cutoff.longValue,
                broker = Address.normalize(event._broker),
                owner = Address.normalize(event._owner),
                marketHash = MarketHash(
                  MarketPair(event._token1, event._token2)
                ).toString
              )
            )
          case Some(
              event: AllOrdersCancelledForTradingPairEvent.Result
              ) =>
            Some(
              CutoffEvent(
                header = Some(eventHeader),
                cutoff = event._cutoff.longValue,
                broker = Address.normalize(event._broker),
                owner = Address.normalize(event._broker),
                marketHash = MarketHash(
                  MarketPair(event._token1, event._token2)
                ).toString
              )
            )
          case Some(event: OrdersCancelledEvent.Result) =>
            Some(
              OrdersCancelledOnChainEvent(
                header = Some(eventHeader),
                broker = Address.normalize(event.address),
                orderHashes = event._orderHashes,
                owner = Address.normalize(event.address)
              )
            )
          case _ =>
            None
        }
    }.filter(_.nonEmpty).map(_.get)
  }

  def extractPendingEvents(tx: Transaction): Seq[Any] =
    loopringProtocolAbi.unpackFunctionInput(tx.input) match {
      case Some(params: CancelAllOrdersForTradingPairFunction.Params) =>
        Seq(
          CutoffEvent(
            broker = tx.from,
            owner = tx.from,
            marketHash = MarketHash(
              MarketPair(params.token1, params.token2)
            ).toString,
            cutoff = params.cutoff.longValue()
          )
        )
      case Some(params: CancelAllOrdersForTradingPairOfOwnerFunction.Params) =>
        Seq(
          CutoffEvent(
            broker = tx.from,
            owner = params.owner,
            marketHash = MarketHash(
              MarketPair(params.token1, params.token2)
            ).toString,
            cutoff = params.cutoff.longValue()
          )
        )
      case Some(params: CancelAllOrdersOfOwnerFunction.Params) =>
        Seq(
          CutoffEvent(
            broker = tx.from,
            owner = params.owner,
            cutoff = params.cutoff.longValue()
          )
        )
      case Some(params: CancelAllOrdersFunction.Params) =>
        Seq(
          CutoffEvent(
            broker = tx.from,
            owner = tx.from,
            cutoff = params.cutoff.longValue()
          )
        )
      case _ => Seq.empty
    }

}
