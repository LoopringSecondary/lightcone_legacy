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

package io.lightcone.relayer.validator

import com.typesafe.config.Config
import io.lightcone.core._
import io.lightcone.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data._
import org.web3j.utils._

import scala.concurrent._

final class CancelOrderValidator(
    implicit
    config: Config,
    ec: ExecutionContext,
    metadataManager: MetadataManager,
    timeProvider: TimeProvider,
    dbModule: DatabaseModule) {
  import ErrorCode._

  /**
    * config.getInt("") 该配置应该放在哪里？
    */
  val validityInSeconds = 600

  //TODO：确定签名规则，单个订单，采用订单的签名简单测试
  def validate(req: CancelOrder.Req) = {
    val id = req.id
    val owner = req.owner
    val marketPair = req.marketPair
    val time = req.time
    val current = timeProvider.getTimeSeconds()

    if (owner.isEmpty || !Address.isValid(owner) || time.isEmpty
        || NumericConversion.toBigInt(time.get) < current - validityInSeconds
        || NumericConversion.toBigInt(time.get) > current) {

      Future.successful(Left(ERR_INVALID_ARGUMENT))
    } else if (id.isEmpty && marketPair.isEmpty) {
      Future.successful(
        Right(
          req.copy(
            owner = Address.normalize(owner)
          )
        )
      )
    } else if (id.isEmpty && marketPair.isDefined) {
      Future {
        try {
          metadataManager.getMarket(marketPair.get)
          Right(
            req.copy(
              owner = Address.normalize(owner),
              marketPair = marketPair.map(_.normalize())
            )
          )
        } catch {
          case _: Throwable =>
            Left(ERR_INVALID_MARKET)
        }
      }
    } else {
      dbModule.orderService.getOrder(req.id).map {
        case Some(order) if order.owner == owner =>
          Right(
            req.copy(
              owner = Address.normalize(req.owner)
            )
          )
        case Some(_) =>
          Left(
            ERR_ORDER_OWNER_UNMATCHED
          )
        case None =>
          Left(ERR_ORDER_NOT_EXIST)
      }
    }
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
