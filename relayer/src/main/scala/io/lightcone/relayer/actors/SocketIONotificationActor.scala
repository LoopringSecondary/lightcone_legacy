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

package io.lightcone.relayer.actors

import akka.actor._
import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.relayer.base._
import io.lightcone.relayer.socketio._
import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.lib.NumericConversion

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      balanceNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForBalanceUpdate
      ],
      transactionNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForTxRecord
      ],
      orderNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrderUpdate
      ],
      tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTicker],
      orderBookNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForOrderbookUpdate
      ],
      tokenMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForTokenMetadata
      ],
      marketMetadataNotifier: SocketIONotifier[
        SocketIOSubscription.ParamsForMarketMetadata
      ],
      deployActorsIgnoringRoles: Boolean
    ): ActorRef = {
    startSingleton(Props(new SocketIONotificationActor()))
  }
}

class SocketIONotificationActor @Inject()(
    implicit
    val system: ActorSystem,
    val ec: ExecutionContext,
    val timeout: Timeout,
    val balanceNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForBalanceUpdate
    ],
    val transactionNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForTxRecord
    ],
    val orderNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrderUpdate
    ],
    val tickerNotifier: SocketIONotifier[SocketIOSubscription.ParamsForTicker],
    val orderBookNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForOrderbookUpdate
    ],
    val tokenMetadataNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForTokenMetadata
    ],
    val marketMetadataNotifier: SocketIONotifier[
      SocketIOSubscription.ParamsForMarketMetadata
    ])
    extends Actor {

  def receive: Receive = {
    // events to deliver to socket.io clients must be generated here, not inside the listeners.

    case event: BalanceUpdate =>
      balanceNotifier.notifyEvent(event)

    case event: AddressBalanceAndAllowanceEvent =>
      val data = BalanceAndAllowanceResponse(
        owner = event.address,
        balanceAndAllowance = TokenBalanceAndAllowance(
          address = event.token,
          balance = NumericConversion.toHexString(event.balance),
          allowance = NumericConversion.toHexString(event.allowance),
          availableBalance =
            NumericConversion.toHexString(event.availableBalance),
          availableAllowance = NumericConversion.toHexString(event.allowance)
        )
      )
      balanceNotifier.notifyEvent(data)

    case event: TxRecordUpdate =>
      transactionNotifier.notifyEvent(event)

    case event: OrderUpdate =>
      orderNotifier.notifyEvent(event)

    case event: RawOrder =>
      val data = Order(
        hash = event.hash,
        owner = event.owner,
        version = event.version,
        tokenB = event.tokenB,
        tokenS = event.tokenS,
        amountB = NumericConversion.toHexString(event.amountB),
        amountS = NumericConversion.toHexString(event.amountS),
        validSince = NumericConversion.toHexString(BigInt(event.validSince)),
        dualAuthAddr = event.params.get.dualAuthAddr,
        broker = event.getParams.broker,
        orderInterceptor = event.getParams.orderInterceptor,
        wallet = event.getParams.wallet,
        validUntil =
          NumericConversion.toHexString(BigInt(event.getParams.validUntil)),
        allOrNone = event.getParams.allOrNone,
        tokenFee = event.getFeeParams.tokenFee,
        amountFee = NumericConversion.toHexString(event.getFeeParams.amountFee),
        tokenRecipient = event.getFeeParams.tokenRecipient,
        status = event.getState.status.name,
        createdAt =
          NumericConversion.toHexString(BigInt(event.getState.createdAt)),
        outstandingAmountS =
          NumericConversion.toHexString(event.getState.outstandingAmountS),
        outstandingAmountB =
          NumericConversion.toHexString(event.getState.outstandingAmountB),
        outstandingAmountFee =
          NumericConversion.toHexString(event.getState.outstandingAmountFee)
      )

      orderNotifier.notifyEvent(data)

    case event: TradeEvent =>
      val data = Trade(
        owner = event.owner,
        orderHash = event.orderHash,
        ringHash = event.ringHash,
        ringIndex = NumericConversion.toHexString(BigInt(event.ringIndex)),
        fillIndex = NumericConversion.toHexString(BigInt(event.fillIndex)),
        tokenS = event.tokenS,
        tokenB = event.tokenB,
        tokenFee = event.tokenFee,
        amountS = NumericConversion.toHexString(event.amountS),
        amountB = NumericConversion.toHexString(event.amountB),
        amountFee = NumericConversion.toHexString(event.amountFee),
        feeAmountS = NumericConversion.toHexString(event.feeAmountS),
        feeAmountB = NumericConversion.toHexString(event.feeAmountB),
        feeRecipient = event.feeRecipient,
        waiveFeePercentage = event.waiveFeePercentage,
        walletSplitPercentage = event.walletSplitPercentage,
        wallet = event.wallet,
        time = NumericConversion.toHexString(BigInt(event.blockTimestamp)),
        blockNum = NumericConversion.toHexString(BigInt(event.blockHeight)),
        txHash = event.txHash,
        miner = event.miner
      )
      tradeNotifier.notifyEvent(data)

    case event: TransferEvent =>
      val data = Transfer(
        owner = event.owner,
        from = event.from,
        to = event.to,
        token = event.token,
        amount = NumericConversion.toHexString(event.amount),
        txHash = event.getHeader.txHash,
        blockNum = NumericConversion.toHexString(
          BigInt(event.getHeader.getBlockHeader.height)
        ),
        time = NumericConversion.toHexString(
          BigInt(event.getHeader.getBlockHeader.timestamp)
        )
      )
      transferNotifier.notifyEvent(data)
  }
}
