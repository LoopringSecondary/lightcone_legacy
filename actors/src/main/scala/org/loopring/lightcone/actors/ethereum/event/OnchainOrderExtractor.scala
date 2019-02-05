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

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.data._
import org.web3j.utils.Numeric

import scala.concurrent._
import org.loopring.lightcone.lib.data._
import org.loopring.lightcone.ethereum.data._

class OnchainOrderExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends EventExtractor[RawOrder] {

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  def extract(block: RawBlockData): Future[Seq[RawOrder]] = Future {
    block.receipts.flatMap { receipt =>
      if (receipt.contractAddress.equalsIgnoreCase(ringSubmitterAddress)) {
        receipt.logs.map { log =>
          loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
            case Some(event: OrderSubmittedEvent.Result) =>
              Some(extractOrderFromEvent(event))
            case _ =>
              None
          }
        }.filter(_.nonEmpty).map(_.get)
      } else {
        Seq.empty
      }
    }
  }

  private def extractOrderFromEvent(
      event: OrderSubmittedEvent.Result
    ): RawOrder = {
    val data = Numeric.cleanHexPrefix(event.orderData)
    RawOrder(
      owner = Address.normalize(data.substring(0, 64)),
      tokenS = Address.normalize(data.substring(64, 64 * 2)),
      tokenB = Address.normalize(data.substring(64 * 2, 64 * 3)),
      amountS = BigInt(Numeric.toBigInt(data.substring(64 * 3, 64 * 4))),
      amountB = BigInt(Numeric.toBigInt(data.substring(64 * 4, 64 * 5))),
      validSince = Numeric.toBigInt(data.substring(64 * 5, 64 * 6)).intValue(),
      params = Some(
        RawOrder.Params(
          broker = Address.normalize(data.substring(64 * 6, 64 * 7)),
          orderInterceptor = Address.normalize(data.substring(64 * 7, 64 * 8)),
          wallet = Address.normalize(data.substring(64 * 8, 64 * 9)),
          validUntil =
            Numeric.toBigInt(data.substring(64 * 9, 64 * 10)).intValue(),
          allOrNone = Numeric
            .toBigInt(data.substring(64 * 10, 64 * 11))
            .intValue() == 1
        )
      ),
      hash = event.orderHash,
      feeParams = Some(
        RawOrder.FeeParams(
          tokenFee = Address.normalize(data.substring(64 * 11, 64 * 12)),
          amountFee = BigInt(Numeric.toBigInt(data.substring(64 * 12, 64 * 13))),
          tokenBFeePercentage =
            Numeric.toBigInt(data.substring(64 * 13, 64 * 14)).intValue(),
          tokenSFeePercentage =
            Numeric.toBigInt(data.substring(64 * 14, 64 * 15)).intValue(),
          tokenRecipient = Address.normalize(data.substring(64 * 15, 64 * 16)),
          walletSplitPercentage =
            Numeric.toBigInt(data.substring(64 * 16, 64 * 17)).intValue()
        )
      ),
      erc1400Params = Some(
        RawOrder.ERC1400Params(
          tokenStandardS = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 17, 64 * 18)).intValue()
          ),
          tokenStandardB = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 18, 64 * 19)).intValue()
          ),
          tokenStandardFee = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 19, 64 * 20)).intValue()
          ),
          trancheS = Numeric.prependHexPrefix(data.substring(64 * 20, 64 * 21)),
          trancheB = Numeric.prependHexPrefix(data.substring(64 * 21, 64 * 22)),
          transferDataS =
            Numeric.prependHexPrefix(data.substring(64 * 22, 64 * 23))
        )
      )
    )
  }
}
