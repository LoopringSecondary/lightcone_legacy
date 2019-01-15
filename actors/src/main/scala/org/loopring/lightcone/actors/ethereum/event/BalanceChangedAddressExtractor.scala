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

package org.loopring.lightcone.actors.ethereum.event

import akka.actor.ActorRef
import com.google.inject.Inject
import org.loopring.lightcone.actors.ethereum.{
  EthereumAccessActor,
  EthereumBatchCallRequestBuilder
}
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.web3j.utils.Numeric
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.proto.{TransferEvent => _, _}

import scala.collection.mutable.ListBuffer
import scala.concurrent._

class BalanceChangedAddressExtractor @Inject()(
    implicit
    brb: EthereumBatchCallRequestBuilder,
    lookup: Lookup[ActorRef],
    timeout: Timeout,
    val ec: ExecutionContext)
    extends EventExtractor[AddressBalanceUpdated] {

  def ethereumAccessor = lookup.get(EthereumAccessActor.name)

  override def extract(
      block: RawBlockData
    ): Future[Seq[AddressBalanceUpdated]] = {
    val miners: Seq[AddressBalanceUpdated] = block.uncles
      .+:(block.miner)
      .map(
        addr =>
          AddressBalanceUpdated(address = addr, token = Address.ZERO.toString())
      )
    for {
      balanceAddresses <- Future
        .sequence((block.txs zip block.receipts).map {
          case (tx, receipt) =>
            extract(tx, receipt, block.timestamp)
        })
        .map(_.flatten)
      distEvents = (balanceAddresses ++ miners).distinct
      (ethAddress, tokenAddresses) = distEvents.partition(
        addr => Address(addr.address).isZero
      )
      batchCallReq = brb.buildRequest(tokenAddresses, "latest")
      tokenBalances <- (ethereumAccessor ? batchCallReq)
        .mapAs[BatchCallContracts.Res]
        .map(_.resps.map(_.result))
        .map(_.map(res => Numeric.toBigInt(res).toByteArray))
      ethBalances <- (lookup.get(EthereumAccessActor.name) ? BatchGetEthBalance
        .Req(
          ethAddress.map(addr => EthGetBalance.Req(address = addr.address))
        ))
        .mapAs[BatchGetEthBalance.Res]
        .map(_.resps.map(res => Numeric.toBigInt(res.result).toByteArray))
    } yield {
      (tokenAddresses zip tokenBalances).map(
        item => item._1.withBalance(item._2)
      ) ++
        (ethAddress zip ethBalances).map(item => item._1.withBalance(item._2))
    }
  }

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Future[Seq[AddressBalanceUpdated]] = Future {
    val balanceAddresses = ListBuffer(
      AddressBalanceUpdated(tx.from, Address.ZERO.toString())
    )
    if (isSucceed(receipt.status) &&
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
    balanceAddresses
  }
}
