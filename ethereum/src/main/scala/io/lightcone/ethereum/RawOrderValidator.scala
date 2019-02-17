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

package io.lightcone.ethereum

import org.web3j.crypto.Hash
import org.web3j.crypto.WalletUtils.isValidAddress
import org.web3j.utils.Numeric
import io.lightcone.core._
import io.lightcone.lib._

trait RawOrderValidator {
  def calculateOrderHash(order: RawOrder): String
  def validate(order: RawOrder): Either[ErrorCode, RawOrder]
}

class RawOrderValidatorImpl extends RawOrderValidator {
  import ErrorCode._

  val FeePercentageBase = 1000
  val Eip191Header = "\u0019\u0001"

  val Eip712OrderSchemaHash =
    "0x40b942178d2a51f1f61934268590778feb8114db632db7d88537c98d2b05c5f2"

  val Eip712DomainHash =
    "0xaea25658c273c666156bd427f83a666135fcde6887a6c25fc1cd1562bc4f3f34"

  def calculateOrderHash(order: RawOrder): String = {
    val sourceBytes = getOrderHashSourceBytes(order)
    Numeric.toHexString(Hash.sha3(sourceBytes))
  }

  def validate(order: RawOrder): Either[ErrorCode, RawOrder] = {
    def checkDualAuthPrivateKey = {
      if (isValidAddress(order.getParams.dualAuthAddr)) {
        val dualAuthPrivateKey = order.getParams.dualAuthPrivateKey
        dualAuthPrivateKey != null && dualAuthPrivateKey.length >= 64
      } else {
        true
      }
    }

    def checkOrderSig = {
      val hashSourceBytes = getOrderHashSourceBytes(order)
      val hashBytes = Hash.sha3(hashSourceBytes)
      val sig = order.getParams.sig
      val sigBytes = Numeric.hexStringToByteArray(sig)

      // Not support OrderRegistry contract for now.
      // All orders here must have signature field.
      if (sigBytes.length < 67) false
      else {
        val algorithm = sigBytes(0)
        val v = sigBytes(2)
        val r = sigBytes.slice(3, 35)
        val s = sigBytes.slice(35, 67)

        algorithm match {
          case SigningAlgorithm.ALGO_ETHEREUM.value =>
            verifyEthereumSignature(hashBytes, r, s, v, Address(order.owner))
          case SigningAlgorithm.ALGO_EIP712.value =>
            verifySignature(hashSourceBytes, r, s, v, Address(order.owner))
          case _ =>
            throw new IllegalArgumentException(
              s"invalid SigningAlgorithm value: $algorithm"
            )
        }

      }
    }

    val checklist = Seq[(Boolean, ErrorCode)](
      (order.version == 0) -> ERR_ORDER_VALIDATION_UNSUPPORTED_VERSION,
      isValidAddress(order.owner) -> ERR_ORDER_VALIDATION_INVALID_OWNER,
      isValidAddress(order.tokenS) -> ERR_ORDER_VALIDATION_INVALID_TOKENS,
      isValidAddress(order.tokenB) -> ERR_ORDER_VALIDATION_INVALID_TOKENB,
      (order.amountS > 0) -> ERR_ORDER_VALIDATION_INVALID_TOKEN_AMOUNT,
      (order.amountB > 0) -> ERR_ORDER_VALIDATION_INVALID_TOKEN_AMOUNT,
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
      checkDualAuthPrivateKey -> ERR_ORDER_VALIDATION_INVALID_MISSING_DUALAUTH_PRIV_KEY,
      checkOrderSig -> ERR_ORDER_VALIDATION_INVALID_SIG
    )

    checklist.span(_._1)._2 match {
      case List() => Right(order)
      case tail   => Left(tail.head._2)
    }
  }

  private def getOrderHashSourceBytes(order: RawOrder) = {
    def strToHex(str: String) = str.getBytes.map("%02X" format _).mkString

    val bitstream = new Bitstream
    val feeParams = order.getFeeParams
    val optionalParams = order.getParams
    val erc1400Params = order.getErc1400Params

    val transferDataBytes = erc1400Params.transferDataS.getBytes
    val transferDataHash = Numeric.toHexString(Hash.sha3(transferDataBytes))

    bitstream.addBytes32(Eip712OrderSchemaHash, true)
    bitstream.addUint(order.amountS, true)
    bitstream.addUint(order.amountB, true)
    bitstream.addUint(feeParams.amountFee, true)
    bitstream.addUint(BigInt(order.validSince), true)
    bitstream.addUint(BigInt(optionalParams.validUntil), true)
    bitstream.addAddress(order.owner, 32, true)
    bitstream.addAddress(order.tokenS, 32, true)
    bitstream.addAddress(order.tokenB, 32, true)
    bitstream.addAddress(optionalParams.dualAuthAddr, 32, true)
    bitstream.addAddress(optionalParams.broker, 32, true)
    bitstream.addAddress(optionalParams.orderInterceptor, 32, true)
    bitstream.addAddress(optionalParams.wallet, 32, true)

    if (isAddressValidAndNonZero(feeParams.tokenRecipient)) {
      bitstream.addAddress(feeParams.tokenRecipient, 32, true)
    } else {
      bitstream.addAddress(order.owner, 32, true)
    }

    bitstream.addAddress(feeParams.tokenFee, 32, true)
    bitstream.addUint(feeParams.walletSplitPercentage)
    bitstream.addUint(feeParams.tokenSFeePercentage)
    bitstream.addUint(feeParams.tokenBFeePercentage)
    bitstream.addUint(if (optionalParams.allOrNone) 1 else 0)
    bitstream.addUint(erc1400Params.tokenStandardS.value)
    bitstream.addUint(erc1400Params.tokenStandardB.value)
    bitstream.addUint(erc1400Params.tokenStandardFee.value)
    bitstream.addBytes32(erc1400Params.trancheS, true)
    bitstream.addBytes32(erc1400Params.trancheB, true)
    bitstream.addBytes32(transferDataHash, true)
    val orderDataHash = Numeric.toHexString(Hash.sha3(bitstream.getBytes))

    val outterStream = new Bitstream
    outterStream.addHex(strToHex(Eip191Header), true)
    outterStream.addBytes32(Eip712DomainHash, true)
    outterStream.addBytes32(orderDataHash, true)

    outterStream.getBytes
  }

}
