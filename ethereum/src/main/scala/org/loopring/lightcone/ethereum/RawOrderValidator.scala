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
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.XErrorCode._

trait RawOrderValidator {
  def setupEmptyFieldsWithDefaults(order: XRawOrder, lrcAddress: String): XRawOrder
  def calculateOrderHash(order: XRawOrder): String
  def validate(order: XRawOrder): Either[XErrorCode, XRawOrder]
}

class RawOrderValidatorImpl extends RawOrderValidator {
  // TODO(Kongliang): the following constant fields should be configurable somewhere.


  val FeePercentageBase = 1000
  val Eip191Header = "\u0019\u0001"
  val Eip712OrderSchemaHash = "0x40b942178d2a51f1f61934268590778feb8114db632db7d88537c98d2b05c5f2"
  val Eip712DomainHash = "0xaea25658c273c666156bd427f83a666135fcde6887a6c25fc1cd1562bc4f3f34"

  def setupEmptyFieldsWithDefaults(order: XRawOrder, lrcAddress: String) = {
    val defaultAddr = "0x0"
    val fullZeroAddr = "0x" + "0" * 40
    val defaultUint256 = ByteString.copyFromUtf8("0")
    val zeroBytes32Str = "0x" + "0" * 64

    val addressGetOrDefault = (addr: String) => if (isValidAddress(addr)) addr else defaultAddr

    val uint256GetOrDefault = (uint256Bs: ByteString) => {
      if (uint256Bs.isEmpty) defaultUint256 else uint256Bs
    }

    var params = order.params.getOrElse(new XRawOrder.Params)
    var feeParams = order.feeParams.getOrElse(new XRawOrder.FeeParams)
    var erc1400Params = order.erc1400Params.getOrElse(new XRawOrder.ERC1400Params)

    params = params.copy(
      dualAuthAddr = addressGetOrDefault(params.dualAuthAddr),
      broker = addressGetOrDefault(params.broker),
      orderInterceptor = addressGetOrDefault(params.orderInterceptor),
      wallet = addressGetOrDefault(params.wallet)
    )

    feeParams = feeParams.copy(
      amountFee = uint256GetOrDefault(feeParams.amountFee),
      tokenRecipient = addressGetOrDefault(feeParams.tokenRecipient)
    )

    if (feeParams.tokenFee.length == 0
      || feeParams.tokenFee == defaultAddr
      || feeParams.tokenFee == fullZeroAddr) {
      feeParams = feeParams.copy(tokenFee = lrcAddress)
    }

    order.copy(
      params = Option(params),
      feeParams = Option(feeParams),
      erc1400Params = Option(erc1400Params),
    )
  }

  def calculateOrderHash(order: XRawOrder): String = {
    val bitstream = new Bitstream
    val feeParams = order.feeParams.get
    val optionalParams = order.params.get
    val erc1400Params = order.erc1400Params.get

    val transferDataBytes = erc1400Params.transferDataS.getBytes
    val transferDataHash = Numeric.toHexString(Hash.sha3(transferDataBytes))

    bitstream.addBytes32(Eip712OrderSchemaHash, true)
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
    bitstream.addUint(optionalParams.tokenStandardS.value)
    bitstream.addUint(optionalParams.tokenStandardB.value)
    bitstream.addUint(optionalParams.tokenStandardFee.value)
    bitstream.addBytes32(erc1400Params.trancheS, true)
    bitstream.addBytes32(erc1400Params.trancheB, true)
    bitstream.addBytes32(transferDataHash, true)

    val orderDataHash = Numeric.toHexString(Hash.sha3(bitstream.getBytes))
    val outterStream = new Bitstream
    outterStream.addBytes32(Eip191Header, true)
    outterStream.addBytes32(Eip712DomainHash, true)
    outterStream.addBytes32(orderDataHash, true)

    Numeric.toHexString(Hash.sha3(outterStream.getBytes))
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
      case List() ⇒ Right(order)
      case tail   ⇒ Left(tail.head._2)
    }
  }

}
