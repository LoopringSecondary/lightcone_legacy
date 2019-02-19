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

package io.lightcone.relayer.ethereum.event

import akka.actor.ActorRef
import com.google.inject.Inject
import io.lightcone.relayer.ethereum._
import io.lightcone.ethereum.abi._
import akka.pattern._
import akka.util.Timeout
import io.lightcone.relayer.base._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.ethereum.event.{TransferEvent => _, _}
import io.lightcone.relayer.data._

import scala.collection.mutable.ListBuffer
import scala.concurrent._

class BalanceChangedAddressExtractor @Inject()(
    implicit
    brb: EthereumBatchCallRequestBuilder,
    actors: Lookup[ActorRef],
    timeout: Timeout,
    val ec: ExecutionContext)
    extends EventExtractor[AddressBalanceUpdatedEvent] {

  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def extract(block: RawBlockData): Future[Seq[AddressBalanceUpdatedEvent]] = {
    val balanceAddresses = ListBuffer.empty[AddressBalanceUpdatedEvent]
    (block.txs zip block.receipts).foreach {
      case (tx, receipt) =>
        balanceAddresses.append(
          AddressBalanceUpdatedEvent(tx.from, Address.ZERO.toString())
        )
        if (isSucceed(receipt.status) &&
            NumericConversion.toBigInt(tx.value) > 0) {
          balanceAddresses.append(
            AddressBalanceUpdatedEvent(tx.to, Address.ZERO.toString())
          )
        }
        receipt.logs.foreach(log => {
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) =>
              balanceAddresses.append(
                AddressBalanceUpdatedEvent(transfer.from, log.address),
                AddressBalanceUpdatedEvent(transfer.receiver, log.address)
              )
            case Some(deposit: DepositEvent.Result) =>
              balanceAddresses.append(
                AddressBalanceUpdatedEvent(deposit.dst, log.address)
              )
            case Some(withdrawal: WithdrawalEvent.Result) =>
              balanceAddresses.append(
                AddressBalanceUpdatedEvent(withdrawal.src, log.address)
              )
            case _ =>
          }
        })
    }
    val miners: Seq[AddressBalanceUpdatedEvent] = block.uncles
      .+:(block.miner)
      .map(
        addr =>
          AddressBalanceUpdatedEvent(
            address = addr,
            token = Address.ZERO.toString()
          )
      )
    val distEvents = (balanceAddresses ++ miners).distinct
    val (ethAddress, tokenAddresses) =
      distEvents.partition(addr => Address(addr.token).isZero)
    val batchCallReq = brb.buildRequest(tokenAddresses, "latest")
    for {
      tokenBalances <- if (tokenAddresses.nonEmpty) {
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
      ethBalances <- if (ethAddress.nonEmpty) {
        (ethereumAccessor ? BatchGetEthBalance
          .Req(
            ethAddress.map(addr => EthGetBalance.Req(address = addr.address))
          ))
          .mapAs[BatchGetEthBalance.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (tokenAddresses zip tokenBalances).map(
        item => item._1.withBalance(item._2)
      ) ++
        (ethAddress zip ethBalances).map(
          item =>
            AddressBalanceUpdatedEvent(
              address = Address.normalize(item._1.address),
              token = Address.normalize(item._1.token),
              balance = item._2
            )
        )
    }
  }

}
