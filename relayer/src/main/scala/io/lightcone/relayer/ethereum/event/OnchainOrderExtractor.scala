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

import com.google.inject.Inject
import com.typesafe.config.Config
import io.lightcone.ethereum.abi._
import io.lightcone.relayer.data._
import io.lightcone.core._
import io.lightcone.ethereum.event._
import io.lightcone.lib._
import org.web3j.utils.Numeric

import scala.concurrent._

class OnchainOrderExtractor @Inject()(
    implicit
    val ec: ExecutionContext,
    val config: Config)
    extends AbstractEventExtractor {

  val ringSubmitterAddress =
    Address(config.getString("loopring_protocol.protocol-address")).toString()

  def extractTx(
      tx: Transaction,
      receipt: TransactionReceipt,
      eventHeader: EventHeader
    ): Future[Seq[AnyRef]] = Future {
    if (receipt.contractAddress.equalsIgnoreCase(ringSubmitterAddress)) {
      receipt.logs.map { log =>
        loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
          case Some(event: OrderSubmittedEvent.Result) =>
            Some(OrderSubmittedOnChainEvent(Some(extractOrderFromEvent(event))))
          case _ =>
            None
        }
      }.filter(_.nonEmpty).map(_.get)
    } else {
      Seq.empty
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
      amountS = NumericConversion.toBigInt(data.substring(64 * 3, 64 * 4)),
      amountB = NumericConversion.toBigInt(data.substring(64 * 4, 64 * 5)),
      validSince =
        NumericConversion.toBigInt(data.substring(64 * 5, 64 * 6)).intValue,
      params = Some(
        RawOrder.Params(
          broker = Address.normalize(data.substring(64 * 6, 64 * 7)),
          orderInterceptor = Address.normalize(data.substring(64 * 7, 64 * 8)),
          wallet = Address.normalize(data.substring(64 * 8, 64 * 9)),
          validUntil = NumericConversion
            .toBigInt(data.substring(64 * 9, 64 * 10))
            .intValue,
          allOrNone = NumericConversion
            .toBigInt(data.substring(64 * 10, 64 * 11))
            .intValue == 1
        )
      ),
      hash = event.orderHash,
      feeParams = Some(
        RawOrder.FeeParams(
          tokenFee = Address.normalize(data.substring(64 * 11, 64 * 12)),
          amountFee =
            NumericConversion.toBigInt(data.substring(64 * 12, 64 * 13)),
          tokenBFeePercentage = NumericConversion
            .toBigInt(data.substring(64 * 13, 64 * 14))
            .intValue,
          tokenSFeePercentage = NumericConversion
            .toBigInt(data.substring(64 * 14, 64 * 15))
            .intValue,
          tokenRecipient = Address.normalize(data.substring(64 * 15, 64 * 16)),
          walletSplitPercentage = NumericConversion
            .toBigInt(data.substring(64 * 16, 64 * 17))
            .intValue
        )
      ),
      erc1400Params = Some(
        RawOrder.ERC1400Params(
          tokenStandardS = TokenStandard.fromValue(
            NumericConversion
              .toBigInt(data.substring(64 * 17, 64 * 18))
              .intValue
          ),
          tokenStandardB = TokenStandard.fromValue(
            NumericConversion
              .toBigInt(data.substring(64 * 18, 64 * 19))
              .intValue
          ),
          tokenStandardFee = TokenStandard.fromValue(
            NumericConversion
              .toBigInt(data.substring(64 * 19, 64 * 20))
              .intValue
          ),
          trancheS = Numeric
            .prependHexPrefix(data.substring(64 * 20, 64 * 21)),
          trancheB = Numeric
            .prependHexPrefix(data.substring(64 * 21, 64 * 22)),
          transferDataS =
            Numeric.prependHexPrefix(data.substring(64 * 22, 64 * 23))
        )
      )
    )
  }
}
