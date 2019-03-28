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

package io.lightcone.ethereum.extractor.block

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.core.Amount
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor._
import io.lightcone.ethereum.extractor.tx.TxTransferEventExtractor
import io.lightcone.lib._
import io.lightcone.relayer.data._
import scala.concurrent.{ExecutionContext, Future}

class BalanceUpdateAddressExtractor @Inject()(
    ethereumAccessor: () => ActorRef,
    extractor: TxTransferEventExtractor
  )(
    implicit
    val timeout: Timeout,
    val ec: ExecutionContext)
    extends EventExtractor[BlockWithTxObject, AddressBalanceUpdatedEvent] {

  def extractEvents(
      source: BlockWithTxObject
    ): Future[Seq[AddressBalanceUpdatedEvent]] = {
    val blockHeader = BlockHeader(
      height = NumericConversion.toBigInt(source.getNumber.value).toLong,
      hash = source.hash,
      miner = source.miner,
      timestamp = NumericConversion.toBigInt(source.getTimestamp).toLong,
      uncles = source.uncleMiners
    )
    val transferAddresses = (source.transactions zip source.receipts).flatMap {
      case (tx, receipt) =>
        val eventHeader = EventHeader(
          blockHeader = Some(blockHeader),
          txHash = tx.hash,
          txFrom = tx.from,
          txTo = tx.to,
          txStatus = receipt.status,
          txValue = tx.value
        )
        extractor
          .extractTransferEvents(
            TransactionData(tx, Some(receipt -> eventHeader))
          )
          .filter { event =>
            event.getHeader.txStatus.isTxStatusSuccess ||
            event.owner == event.from
          }
          .map { transfer =>
            AddressBalanceUpdatedEvent(
              address = transfer.owner,
              token = transfer.token
            )
          }
    }.distinct

    val senderAddresses = source.transactions.map { tx =>
      AddressBalanceUpdatedEvent(
        address = tx.from,
        token = Address.ZERO.toString()
      )
    }.distinct
    val addresses = transferAddresses ++ senderAddresses

    val (ethAddresses, tokenAddresses) =
      addresses.partition(_.token == Address.ZERO.toString())
    for {
      tokenBalances <- getTokenBalances(tokenAddresses)
      ethBalances <- getEthBalances(ethAddresses)
    } yield tokenBalances ++ ethBalances
  }

  def getTokenBalances(
      addresses: Seq[AddressBalanceUpdatedEvent]
    ): Future[Seq[AddressBalanceUpdatedEvent]] = {
    val balanceCallReqs = addresses.zipWithIndex.map {
      case (address, index) =>
        val data = erc20Abi.balanceOf.pack(
          BalanceOfFunction.Parms(_owner = address.address.toString)
        )
        val param = TransactionParams(to = address.token, data = data)
        EthCall.Req(index, Some(param), "latest")
    }
    val batchCallReq =
      BatchCallContracts.Req(balanceCallReqs, returnBlockNum = true)
    for {
      tokenBalances <- {
        if (addresses.isEmpty) Future.successful(Seq.empty)
        else {
          (ethereumAccessor() ? batchCallReq)
            .mapTo[BatchCallContracts.Res]
            .map { resp =>
              resp.resps.map { r =>
                Amount(
                  NumericConversion.toAmount(r.result).value,
                  block = resp.block
                )
              }
            }
        }
      }
      result = (addresses zip tokenBalances).map { item =>
        item._1.withBalance(item._2)
      }
    } yield result
  }

  def getEthBalances(
      addresses: Seq[AddressBalanceUpdatedEvent]
    ): Future[Seq[AddressBalanceUpdatedEvent]] = {
    for {
      ethBalances <- {
        if (addresses.isEmpty) Future.successful(Seq.empty)
        else {
          (ethereumAccessor() ? BatchGetEthBalance.Req(
            addresses.map(addr => EthGetBalance.Req(address = addr.address)),
            returnBlockNum = true
          )).mapTo[BatchGetEthBalance.Res].map { resp =>
            resp.resps.map { r =>
              Amount(NumericConversion.toAmount(r.result).value, resp.block)
            }
          }
        }
      }
      result = (addresses zip ethBalances).map { item =>
        item._1.withBalance(item._2)
      }
    } yield result
  }
}
