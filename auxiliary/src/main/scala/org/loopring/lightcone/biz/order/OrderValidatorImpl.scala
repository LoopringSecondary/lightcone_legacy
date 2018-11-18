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

package org.loopring.lightcone.auxiliary.order

import com.google.inject.Inject
import com.typesafe.config.Config
import org.loopring.lightcone.auxiliary.model.Order

class OrderValidatorImpl @Inject() (config: Config) extends OrderValidator {

  val constFalse = false
  val addrLength = 20
  val hashLength = 32
  val validateConfig: OrderValidateConfig = OrderValidateConfig.fromConfig(config)

  val ORDER_IS_EMPTY = ValidateResult(constFalse, "order is empty")
  val MARKET_ORDER_MUST_HAVE_PRIVATE_KEY = ValidateResult(constFalse, "market order must have private key")
  val ORDER_HASH_LENGTH_INCORRECT = ValidateResult(constFalse, s"order hash length is less than $hashLength")
  val TOKEN_S_AND_TOKEN_B_SAME = ValidateResult(constFalse, "tokenS and tokenB can't be same")
  val VALID_SINCE_TOO_FAR = ValidateResult(constFalse, s"order must be valid before ${now - validateConfig.maxValidSinceInterval} second timestamp")
  val VALID_UNTIL_BEFORE_NOW = ValidateResult(constFalse, "valid until is early than now")
  val MARGIN_SPLIT_OUT_OF_RANGE = ValidateResult(constFalse, s"margin split percentage must be from ${validateConfig.minSplitPercentage} to ${validateConfig.maxSplitPercentage}")
  val PROTOCOL_AND_DELEGATE_ADDRESS_NOT_MATCH = ValidateResult(constFalse, "protocol not match with delegateAddress")
  val LESS_LRC_HOLD_THAN_THRESHOLD = ValidateResult(constFalse, s"user hold lrc less than ${validateConfig.minLrcHold}")
  def lengthUnCorrectErr(src: String) = ValidateResult(constFalse, s"$src length is less than $addrLength")
  def now(): Long = System.currentTimeMillis / 1000

  override def validate(order: Order): ValidateResult = {

    order match {
      case o if o == null ⇒ ORDER_IS_EMPTY
      // length check
      case o if o.rawOrder.rawOrderEssential.hash.length != hashLength ⇒ ORDER_HASH_LENGTH_INCORRECT
      case o if o.rawOrder.rawOrderEssential.tokenS.length != addrLength ⇒ lengthUnCorrectErr("tokenS")
      case o if o.rawOrder.rawOrderEssential.tokenB.length != addrLength ⇒ lengthUnCorrectErr("tokenB")
      //      case o if o.rawOrder.get.protocol.length != addrLength ⇒ lengthUnCorrectErr("protocol")
      //      case o if o.rawOrder.get.delegateAddress.length != addrLength ⇒ lengthUnCorrectErr("delegateAddress")
      case o if o.rawOrder.rawOrderEssential.owner.length != addrLength ⇒ lengthUnCorrectErr("owner")
      // tokenS and tokenB can't be same
      case o if o.rawOrder.rawOrderEssential.tokenS == o.rawOrder.rawOrderEssential.tokenB ⇒ TOKEN_S_AND_TOKEN_B_SAME
      // valid since and until check
      case o if (o.rawOrder.rawOrderEssential.validSince - validateConfig.maxValidSinceInterval) > now ⇒ VALID_SINCE_TOO_FAR
      case o if o.rawOrder.rawOrderEssential.validUntil < now ⇒ VALID_UNTIL_BEFORE_NOW
      // margin split check
      //      case o if o.rawOrder.get.marginSplitPercentage / 100.0 > validateConfig.maxSplitPercentage ⇒ MARGIN_SPLIT_OUT_OF_RANGE
      //      case o if o.rawOrder.get.marginSplitPercentage / 100.0 < validateConfig.minSplitPercentage ⇒ MARGIN_SPLIT_OUT_OF_RANGE
      //TODO(xiaolu) min lrc hold check, need access account actor to get balance
      // case o if isLrcHoldLess =>  LESS_LRC_HOLD_THAN_THRESHOLD
      //TODO(xiaolu) check protocol and delegateAddress. need fukun apply method
      // case o if isProtocolMatched =>  PROTOCOL_AND_DELEGATE_ADDRESS_NOT_MATCH
      // market order must apply auth private key
      //todo:
      //      case o if o.orderType == OrderType.MARKET && o.rawOrder.get.dualPrivateKey.isEmpty ⇒ MARKET_ORDER_MUST_HAVE_PRIVATE_KEY
      //TODO(xiaolu) token s min amount check, need apply token amount convert method
      //      case o if (o.rawOrder.get.amountS).toBigInt < validateConfig.minAmountS[o.rawOrder.get.TokenS] =>
      //TODO(xiaolu) token s and token b if in the supported token list
      //TODO(xiaolu) cutoff check
      //TODO(xiaolu) sign check
      //TODO(xiaolu) pow check
    }
  }

}
