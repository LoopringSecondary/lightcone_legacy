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
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.data._
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric

import scala.math.BigInt

trait OrderGenerateSupport {

  def createRawOrder(
      tokenS: String = LRC_TOKEN.address,
      tokenB: String = WETH_TOKEN.address,
      amountS: BigInt = "10".zeros(18),
      amountB: BigInt = "1".zeros(18),
      tokenFee: String = LRC_TOKEN.address,
      amountFee: BigInt = "3".zeros(18),
      validSince: Int = (timeProvider.getTimeMillis / 1000).toInt,
      validUntil: Int = (timeProvider.getTimeMillis / 1000).toInt + 20000
    )(
      implicit
      credentials: Credentials = accounts(0)
    ) = {
    val createAt = timeProvider.getTimeMillis
    val marketId = MarketHash(MarketPair(tokenS, tokenB)).longId
    val owner = credentials.getAddress
    val order = RawOrder(
      owner = owner,
      version = 0,
      tokenS = tokenS,
      tokenB = tokenB,
      amountS = ByteString.copyFrom(amountS.toByteArray),
      amountB = ByteString.copyFrom(amountB.toByteArray),
      validSince = validSince,
      state = Some(
        RawOrder.State(
          createdAt = createAt,
          updatedAt = createAt,
          status = OrderStatus.STATUS_NEW
        )
      ),
      feeParams = Some(
        RawOrder.FeeParams(
          tokenFee = tokenFee,
          amountFee = ByteString.copyFrom(amountFee.toByteArray)
        )
      ),
      params = Some(RawOrder.Params(validUntil = validUntil)),
      marketId = marketId,
      marketEntityId = MarketManagerActor
        .getEntityId(MarketPair(tokenS, tokenB)),
      accountEntityId = MultiAccountManagerActor
        .getEntityId(owner)
    )

    val hash = RawOrderValidatorDefault.calculateOrderHash(order)
    order
      .withHash(hash)
      .withParams(
        order.params.get.withSig(
          Protocol2RingBatchGenerator
            .signPrefixedMessage(
              hash,
              Numeric
                .toHexStringWithPrefix(credentials.getEcKeyPair.getPrivateKey)
            )
        )
      )

  }

}
