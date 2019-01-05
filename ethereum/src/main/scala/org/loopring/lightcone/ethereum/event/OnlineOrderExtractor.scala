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

package org.loopring.lightcone.ethereum.event

import org.loopring.lightcone.ethereum.abi.OrderSubmittedEvent
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric

class OnlineOrderExtractor() extends DataExtractor[RawOrder] {

  def extract(
      tx: Transaction,
      receipt: TransactionReceipt,
      blockTime: String
    ): Seq[RawOrder] = {
    receipt.logs.map { log ⇒
      loopringProtocolAbi.unpackEvent(log.data, log.topics.toArray) match {
        case Some(event: OrderSubmittedEvent.Result) ⇒
          Some(extractOrderFromEvent(event))
        case _ ⇒
          None
      }
    }.filter(_.nonEmpty).map(_.get)
  }

  private def extractOrderFromEvent(
      event: OrderSubmittedEvent.Result
    ): RawOrder = {
    // 去掉head 2 * 64
    val data = Numeric.cleanHexPrefix(event.orderData).substring(128)
    RawOrder(
      owner = Numeric.prependHexPrefix(data.substring(0, 64)),
      tokenS = Numeric.prependHexPrefix(data.substring(64, 64 * 2)),
      tokenB = Numeric.prependHexPrefix(data.substring(64 * 2, 64 * 3)),
      amountS = Numeric.toBigInt(data.substring(64 * 3, 64 * 4)).toByteArray,
      amountB = Numeric.toBigInt(data.substring(64 * 4, 64 * 5)).toByteArray,
      validSince = Numeric.toBigInt(data.substring(64 * 5, 64 * 6)).intValue(),
      params = Some(
        RawOrder.Params(
          broker = Numeric.prependHexPrefix(data.substring(64 * 6, 64 * 7)),
          orderInterceptor =
            Numeric.prependHexPrefix(data.substring(64 * 7, 64 * 8)),
          wallet = Numeric.prependHexPrefix(data.substring(64 * 8, 64 * 9)),
          validUntil =
            Numeric.toBigInt(data.substring(64 * 9, 64 * 10)).intValue(),
          allOrNone = Numeric
            .toBigInt(data.substring(64 * 10, 64 * 11))
            .intValue() == 1,
          tokenStandardS = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 17, 64 * 18)).intValue()
          ),
          tokenStandardB = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 18, 64 * 19)).intValue()
          ),
          tokenStandardFee = TokenStandard.fromValue(
            Numeric.toBigInt(data.substring(64 * 19, 64 * 20)).intValue()
          )
        )
      ),
      hash = event.orderHash,
      feeParams = Some(
        RawOrder.FeeParams(
          tokenFee = Numeric.prependHexPrefix(data.substring(64 * 11, 64 * 12)),
          amountFee =
            Numeric.toBigInt(data.substring(64 * 12, 64 * 13)).toByteArray,
          tokenBFeePercentage =
            Numeric.toBigInt(data.substring(64 * 13, 64 * 14)).intValue(),
          tokenSFeePercentage =
            Numeric.toBigInt(data.substring(64 * 14, 64 * 15)).intValue(),
          tokenRecipient =
            Numeric.prependHexPrefix(data.substring(64 * 15, 64 * 16)),
          walletSplitPercentage =
            Numeric.toBigInt(data.substring(64 * 16, 64 * 17)).intValue()
        )
      ),
      erc1400Params = Some(
        RawOrder.ERC1400Params(
          trancheS = Numeric.prependHexPrefix(data.substring(64 * 20, 64 * 21)),
          trancheB = Numeric.prependHexPrefix(data.substring(64 * 21, 64 * 22)),
          transferDataS =
            Numeric.prependHexPrefix(data.substring(64 * 22, 64 * 23))
        )
      )
    )
  }
}
