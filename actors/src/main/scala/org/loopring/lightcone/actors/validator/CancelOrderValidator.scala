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

package org.loopring.lightcone.actors.validator

import com.typesafe.config.Config
import org.loopring.lightcone.core._
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.{ErrorException, TimeProvider}
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.proto._
import org.web3j.utils._

import scala.concurrent._
import scala.util.Try

final class CancelOrderValidator(
    implicit
    ec: ExecutionContext,
    metadataManager: MetadataManager)
    extends MessageValidator {
  import ErrorCode._

  //TODO：确定签名规则，单个订单，采用订单的签名简单测试
  override def validate = {
    case req: CancelOrder.Req =>
      Future {
        metadataManager.assertMarketPairIsActive(req.getMarketPair)
        if (!checkSign(req.owner, req.id, req.sig))
          throw ErrorException(
            ERR_ORDER_VALIDATION_INVALID_CANCEL_SIG,
            s"not authorized to cancel this order $req.id"
          )
        req
      }
    case _ => throw ErrorException(ERR_INVALID_ARGUMENT)
  }

  private def checkSign(
      owner: String,
      data: String,
      sig: String
    ): Boolean = {
    val sigBytes = Numeric.hexStringToByteArray(sig)
    val v = sigBytes(2)
    val r = sigBytes.slice(3, 35)
    val s = sigBytes.slice(35, 67)
    verifyEthereumSignature(
      Numeric.hexStringToByteArray(data),
      r,
      s,
      v,
      Address(owner)
    )
  }
}
