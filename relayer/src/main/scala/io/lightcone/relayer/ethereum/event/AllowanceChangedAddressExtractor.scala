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
import akka.pattern._
import akka.util.Timeout
import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.base._
import io.lightcone.relayer.ethereum._
import io.lightcone.ethereum.abi._
import io.lightcone.core._
import io.lightcone.lib._
import io.lightcone.ethereum.event.{TransferEvent => _, _}
import io.lightcone.relayer.data._

import scala.collection.mutable.ListBuffer
import scala.concurrent._

class AllowanceChangedAddressExtractor @Inject()(
    implicit
    val config: Config,
    val brb: EthereumBatchCallRequestBuilder,
    val timeout: Timeout,
    val actors: Lookup[ActorRef],
    val ec: ExecutionContext)
    extends EventExtractor[AddressAllowanceUpdatedEvent] {

  val protocolConf = config.getConfig("loopring_protocol")
  val delegateAddress = Address(protocolConf.getString("delegate-address"))
  val protocolAddress = Address(protocolConf.getString("protocol-address"))

  def ethereumAccessor = actors.get(EthereumAccessActor.name)

  def extract(
      block: RawBlockData
    ): Future[Seq[AddressAllowanceUpdatedEvent]] = {
    val allowanceAddresses = ListBuffer.empty[AddressAllowanceUpdatedEvent]
    (block.txs zip block.receipts).foreach {
      case (tx, receipt) =>
        receipt.logs.foreach { log =>
          wethAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(transfer: TransferEvent.Result) =>
              if (Address(receipt.to).equals(protocolAddress))
                allowanceAddresses.append(
                  AddressAllowanceUpdatedEvent(transfer.from, log.address)
                )

            case Some(approval: ApprovalEvent.Result) =>
              if (Address(approval.spender).equals(delegateAddress))
                allowanceAddresses.append(
                  AddressAllowanceUpdatedEvent(approval.owner, log.address)
                )
            case _ =>
          }
        }
        if (isSucceed(receipt.status)) {
          wethAbi.unpackFunctionInput(tx.input) match {
            case Some(param: ApproveFunction.Parms) =>
              if (Address(param.spender).equals(delegateAddress))
                allowanceAddresses.append(
                  AddressAllowanceUpdatedEvent(tx.from, tx.to)
                )
            case _ =>
          }
        }
    }
    val events = allowanceAddresses.distinct
    for {
      tokenAllowances <- if (events.nonEmpty) {
        val batchCallReq = brb.buildRequest(delegateAddress, events, "latest")
        (ethereumAccessor ? batchCallReq)
          .mapAs[BatchCallContracts.Res]
          .map(
            _.resps
              .map(res => NumericConversion.toBigInt(res.result))
          )
      } else {
        Future.successful(Seq.empty)
      }
    } yield {
      (events zip tokenAllowances).map(
        item =>
          AddressAllowanceUpdatedEvent(
            address = Address.normalize(item._1.address),
            token = Address.normalize(item._1.token),
            allowance = item._2
          )
      )
    }
  }

}