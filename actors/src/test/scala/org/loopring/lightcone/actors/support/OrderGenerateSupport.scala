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

package org.loopring.lightcone.actors.support

import com.google.protobuf.ByteString
import org.loopring.lightcone.ethereum.{
  RawOrderValidatorImpl,
  RingBatchGeneratorImpl
}
import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto._
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

trait OrderGenerateSupport {
  my: CommonSpec =>

  def createRawOrder(
      owner: String = "0x53a356c45cffc4c5d4e54bbececb60dbf5de9c8b",
      tokenS: String = LRC_TOKEN.address,
      tokenB: String = WETH_TOKEN.address,
      amountS: BigInt = "10".zeros(18),
      amountB: BigInt = "1".zeros(18),
      tokenFee: String = LRC_TOKEN.address,
      amountFee: BigInt = "3".zeros(18)
    )(
      implicit privateKey: Option[String] = None
    ) = {
    val createAt = timeProvider.getTimeMillis

    val order = XRawOrder(
      owner = owner,
      version = 0,
      tokenS = tokenS,
      tokenB = tokenB,
      amountS = ByteString.copyFrom(amountS.toByteArray),
      amountB = ByteString.copyFrom(amountB.toByteArray),
      validSince = (createAt / 1000).toInt + 10000,
      state = Some(
        XRawOrder.State(
          createdAt = createAt,
          updatedAt = createAt,
          status = XOrderStatus.STATUS_NEW
        )
      ),
      feeParams = Some(
        XRawOrder.FeeParams(
          tokenFee = tokenFee,
          amountFee = ByteString.copyFrom(amountFee.toByteArray)
        )
      ),
      params =
        Some(XRawOrder.Params(validUntil = (createAt / 1000).toInt + 20000))
    )

    val hash = RawOrderValidatorImpl.calculateOrderHash(order)
    order
      .withHash(hash)
      .withParams(
        order.params.get.withSig(
          RingBatchGeneratorImpl
            .signPrefixedMessage(
              hash,
              privateKey.getOrElse(
                "0x6549df526c28b1d92b0de63606cf039d3dc1846b114118367d8b161ec03256bf"
              )
            )
        )
      )

  }

}
