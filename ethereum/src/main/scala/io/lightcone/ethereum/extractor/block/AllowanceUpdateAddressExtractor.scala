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
import akka.util.Timeout
import com.google.inject.Inject
import io.lightcone.ethereum.BlockHeader
import io.lightcone.ethereum.abi._
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor._
import io.lightcone.ethereum.extractor.tx.TxApprovalEventExtractor
import io.lightcone.lib._
import io.lightcone.relayer.data._
import akka.pattern._
import com.typesafe.config.Config
import io.lightcone.core.Amount

import scala.concurrent.{ExecutionContext, Future}

class AllowanceUpdateAddressExtractor @Inject()(
    ethereumAccessor: () => ActorRef,
    extractor: TxApprovalEventExtractor
  )(
    implicit
    val timeout: Timeout,
    val ec: ExecutionContext,
    val config: Config)
    extends EventExtractor[BlockWithTxObject, AddressAllowanceUpdatedEvent] {

  val delegateAddress =
    Address.normalize(config.getString("loopring_protocol.delegate-address"))

  def extractEvents(
      source: BlockWithTxObject
    ): Future[Seq[AddressAllowanceUpdatedEvent]] = {

    val blockHeader = BlockHeader(
      height = NumericConversion.toBigInt(source.getNumber.value).toLong,
      hash = source.hash,
      miner = source.miner,
      timestamp = NumericConversion.toBigInt(source.getTimestamp).toLong,
      uncles = source.uncleMiners
    )
    val addresses = (source.transactions zip source.receipts).flatMap {
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
          .extractApproveEvents(
            TransactionData(tx, Some(receipt -> eventHeader))
          )
    }.filter(_.spender == delegateAddress)
      .map { approval =>
        AddressAllowanceUpdatedEvent(
          address = approval.owner,
          token = approval.token
        )
      }
      .distinct

    getAllowances(addresses)
  }

  def getAllowances(
      addresses: Seq[AddressAllowanceUpdatedEvent]
    ): Future[Seq[AddressAllowanceUpdatedEvent]] = {
    val balanceCallReqs = addresses.zipWithIndex.map {
      case (address, index) =>
        val data = erc20Abi.allowance.pack(
          AllowanceFunction
            .Parms(_owner = address.address, _spender = delegateAddress)
        )
        val param = TransactionParams(to = address.token, data = data)
        EthCall.Req(index, Some(param), "latest")
    }
    val batchCallReq = BatchCallContracts.Req(balanceCallReqs, true)
    for {
      tokenAllowances <- {
        if (addresses.isEmpty) Future.successful(Seq.empty)
        else {
          (ethereumAccessor() ? batchCallReq)
            .mapTo[BatchCallContracts.Res]
            .map { resp =>
              resp.resps.map { r =>
                Amount(NumericConversion.toAmount(r.result).value, resp.block)
              }
            }

        }
      }
      result = (addresses zip tokenAllowances).map { item =>
        item._1.withAllowance(item._2)
      }
    } yield result
  }

}
