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
import io.lightcone.lib.NumericConversion

import scala.concurrent.ExecutionContext

object SocketIONotificationActor extends DeployedAsSingleton {
  val name = "socketio_notifier"

  def start(
      implicit
      system: ActorSystem,
      ec: ExecutionContext,
      timeout: Timeout,
      balanceNotifier: SocketIONotifier[SubscribeBalanceAndAllowance],
      transactionNotifier: SocketIONotifier[SubscribeTransaction],
      orderNotifier: SocketIONotifier[SubscribeOrder],
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
    val balanceNotifier: SocketIONotifier[SubscribeBalanceAndAllowance],
    val transactionNotifier: SocketIONotifier[SubscribeTransaction],
    val orderNotifier: SocketIONotifier[SubscribeOrder])
    extends Actor {

  def receive: Receive = {
    // events to deliver to socket.io clients must be generated here, not inside the listeners.
    case event: AddressBalanceAndAllowanceEvent =>
      val data = BalanceAndAllowanceResponse(
        owner = event.address,
        balanceAndAllowance = TokenBalanceAndAllowance(
          address = event.token,
          balance = event.balance,
          allowance = event.allowance,
          availableBalance = event.availableBalance,
          availableAllowance = event.allowance
        )
      )
      balanceNotifier.notifyEvent(data)

    case event: TransactionEvent =>
      val transaction = TransactionResponse(
        owner = event.owner,
        transaction = Transaction(
          from = event.from,
          to = event.to,
          value = event.value,
          gasPrice = event.gasPrice,
          gasLimit = event.gas,
          gasUsed = event.gasUsed,
          data = event.input,
          nonce = event.nonce,
          hash = event.hash,
          blockNum = event.blockNumber,
          time = event.time,
          status = event.status.name.substring(10),
          `type` = "0x" + event.`type`.toHexString
        )
      )

      transactionNotifier.notifyEvent(transaction)

    case event: RawOrder =>
      val data = Order(
        owner = event.owner,
        version = event.version,
        tokenB = event.tokenB,
        tokenS = event.tokenS,
        amountB = event.amountB,
        amountS = event.amountS,
        validSince = NumericConversion.toHexString(BigInt(event.validSince)),
        dualAuthAddr = event.params.get.dualAuthAddr,
        broker = event.getParams.broker,
        orderInterceptor = event.getParams.orderInterceptor,
        wallet = event.getParams.wallet,
        validUntil =
          NumericConversion.toHexString(BigInt(event.getParams.validUntil)),
        allOrNone = event.getParams.allOrNone,
        tokenFee = event.getFeeParams.tokenFee,
        amountFee = event.getFeeParams.amountFee,
        waiveFeePercentage = event.getFeeParams.waiveFeePercentage,
        tokenSFeePercentage = event.getFeeParams.tokenSFeePercentage,
        tokenBFeePercentage = event.getFeeParams.tokenBFeePercentage,
        tokenRecipient = event.getFeeParams.tokenRecipient,
        walletSplitPercentage = event.getFeeParams.walletSplitPercentage,
        status = event.getState.status.name,
        createdAt =
          NumericConversion.toHexString(BigInt(event.getState.createdAt)),
        outstandingAmountS = event.getState.outstandingAmountS,
        outstandingAmountB = event.getState.outstandingAmountB,
        outstandingAmountFee = event.getState.outstandingAmountFee
      )

      orderNotifier.notifyEvent(data)
  }
}
