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

package org.loopring.lightcone.ethereum.data

import org.web3j.crypto.Hash
import org.web3j.crypto.WalletUtils.isValidAddress
import org.web3j.utils.Numeric
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._

trait RawOrderValidator {
  def calculateOrderHash(order: XRawOrder): String
  def validate(order: XRawOrder): Either[XErrorCode, XRawOrder]
}

// TODO(kongliang): implement and test this class
class RawOrderValidatorImpl extends RawOrderValidator {
  // TODO this field should be configurable somewhere.
  val FeePercentageBase = 1000

  def calculateOrderHash(order: XRawOrder): String = {
    val bitstream = new Bitstream
    val feeParams = order.feeParams.get
    val optionalParams = order.params.get
    bitstream.addUint(order.amountS.toStringUtf8, true)
    bitstream.addUint(order.amountB.toStringUtf8, true)
    bitstream.addUint(feeParams.amountFee.toStringUtf8, true)
    bitstream.addUint(BigInt(order.validSince), true)
    bitstream.addUint(BigInt(optionalParams.validUntil), true)
    bitstream.addAddress(order.owner, true)
    bitstream.addAddress(order.tokenS, true)
    bitstream.addAddress(order.tokenB, true)
    bitstream.addAddress(optionalParams.dualAuthAddr, true)
    bitstream.addAddress(optionalParams.broker, true)
    bitstream.addAddress(optionalParams.orderInterceptor, true)
    bitstream.addAddress(optionalParams.wallet, true)
    bitstream.addAddress(feeParams.tokenRecipient, true)
    bitstream.addAddress(feeParams.tokenFee, true)
    bitstream.addUint16(feeParams.walletSplitPercentage)
    bitstream.addUint16(feeParams.tokenSFeePercentage)
    bitstream.addUint16(feeParams.tokenBFeePercentage)
    bitstream.addBoolean(optionalParams.allOrNone)

    Numeric.toHexString(Hash.sha3(bitstream.getBytes))
  }

  def validate(order: XRawOrder): Either[XErrorCode, XRawOrder] = {
    def checkDualAuthSig = {
      if (isValidAddress(order.params.get.dualAuthAddr)) {
        val authSig = order.params.get.dualAuthSig
        authSig != null && authSig.length > 0
      } else {
        true
      }
    }

    val checklist = Seq[(Boolean, XErrorCode)](
      (order.version == 0) -> ERR_ORDER_VALIDATION_UNSUPPORTED_VERSION,
      isValidAddress(order.owner) -> ERR_ORDER_VALIDATION_INVALID_OWNER,
      isValidAddress(order.tokenS) -> ERR_ORDER_VALIDATION_INVALID_TOKENS,
      isValidAddress(order.tokenB) -> ERR_ORDER_VALIDATION_INVALID_TOKENB,
      (BigInt(order.amountS.toStringUtf8, 16) > 0) -> ERR_ORDER_VALIDATION_INVALID_TOKEN_AMOUNT,
      (BigInt(order.amountB.toStringUtf8, 16) > 0) -> ERR_ORDER_VALIDATION_INVALID_TOKEN_AMOUNT,
      (BigInt(order.feeParams.get.waiveFeePercentage) <= FeePercentageBase)
        -> ERR_ORDER_VALIDATION_INVALID_WAIVE_PERCENTAGE,
      (BigInt(order.feeParams.get.waiveFeePercentage) >= -FeePercentageBase)
        -> ERR_ORDER_VALIDATION_INVALID_WAIVE_PERCENTAGE,
      (BigInt(order.feeParams.get.tokenSFeePercentage) <= FeePercentageBase)
        -> ERR_ORDER_VALIDATION_INVALID_FEE_PERCENTAGE,
      (BigInt(order.feeParams.get.tokenBFeePercentage) <= FeePercentageBase)
        -> ERR_ORDER_VALIDATION_INVALID_FEE_PERCENTAGE,
      (BigInt(order.feeParams.get.walletSplitPercentage) <= 100)
        -> ERR_ORDER_VALIDATION_INVALID_WALLET_SPLIT_PERCENTAGE,
      checkDualAuthSig -> ERR_ORDER_VALIDATION_INVALID_MISSING_DUALAUTH_SIG
    )

    checklist.span(_._1)._2 match {
      case List() => Right(order)
      case tail   => Left(tail.head._2)
    }
  }

}
