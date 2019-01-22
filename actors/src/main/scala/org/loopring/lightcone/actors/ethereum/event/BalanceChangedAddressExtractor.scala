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
import org.loopring.lightcone.actors.ethereum._
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
    actors: Lookup[ActorRef],
    timeout: Timeout,
    val ec: ExecutionContext)
    extends EventExtractor[AddressBalanceUpdated] {

  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def extract(block: RawBlockData): Future[Seq[AddressBalanceUpdated]] = {
    val balanceAddresses = ListBuffer.empty[AddressBalanceUpdated]
    (block.txs zip block.receipts).foreach {
      case (tx, receipt) =>
        balanceAddresses.append(
          AddressBalanceUpdated(tx.from, Address.ZERO.toString())
        )
        if (isSucceed(receipt.status) &&
            BigInt(Numeric.toBigInt(formatHex(tx.value))) > 0) {
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
    }
    val miners: Seq[AddressBalanceUpdated] = block.uncles
      .+:(block.miner)
      .map(
        addr =>
          AddressBalanceUpdated(address = addr, token = Address.ZERO.toString())
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
              .map(res => BigInt(Numeric.toBigInt(formatHex(res.result))))
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
              .map(res => BigInt(Numeric.toBigInt(formatHex(res.result))))
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (tokenAddresses zip tokenBalances).map(
        item => item._1.withBalance(item._2)
      ) ++
        (ethAddress zip ethBalances).map(item => item._1.withBalance(item._2))
    }
  }

}
