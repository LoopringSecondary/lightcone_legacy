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
import io.lightcone.core.MarketMetadata.Status._
import io.lightcone.core.OrderStatus._
import io.lightcone.core._
import io.lightcone.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.data._
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.native.JsonMethods.parse
import org.web3j.utils._

import scala.concurrent._

final class CancelOrderValidator(
    implicit
    config: Config,
    ec: ExecutionContext,
    metadataManager: MetadataManager,
    timeProvider: TimeProvider,
    eip712Support: EIP712Support,
    dbModule: DatabaseModule) {
  import ErrorCode._

  val validityInSeconds = config.getInt("order_cancel.validity-in-seconds")
  val schema = config.getString("order_cancel.schema").stripMargin
  val schemaJson = parse(schema)
  implicit val formats = DefaultFormats

  def validate(
      req: CancelOrder.Req
    ): Future[Either[ErrorCode, CancelOrder.Req]] = {
    val current = timeProvider.getTimeSeconds()

    if (req.owner.isEmpty ||
        !Address.isValid(req.owner) ||
        req.time.isEmpty ||
        NumericConversion.toBigInt(req.getTime) < current - validityInSeconds
        || NumericConversion.toBigInt(req.getTime) > current) {
      Future.successful(Left(ERR_INVALID_ARGUMENT))

    } else if (!checkSign(req)) {
      Future.successful(Left(ERR_INVALID_SIG))
    } else {
      req match {
        case CancelOrder.Req("", owner, _, None, _, _) =>
          Future.successful(Right(req.copy(owner = Address.normalize(owner))))

        case CancelOrder.Req("", owner, _, Some(marketPair), _, _) =>
          Future {
            try {
              metadataManager.isMarketStatus(marketPair, ACTIVE, READONLY)
              Right(
                req.copy(
                  owner = Address.normalize(owner),
                  marketPair = Some(marketPair.normalize())
                )
              )
            } catch {
              case _: Throwable =>
                Left(ERR_INVALID_MARKET)
            }
          }

        case CancelOrder.Req(id, owner, _, _, _, _) =>
          dbModule.orderService.getOrder(req.id).map {
            case Some(order) if order.owner == owner =>
              //TODO(HONGYU,YONGFENG): 订单状态的变迁需要确定规则，另外是否需要在此处过滤
              if (order.getState.status == STATUS_NEW ||
                  order.getState.status == STATUS_PENDING ||
                  order.getState.status == STATUS_PENDING_ACTIVE ||
                  order.getState.status == STATUS_PARTIALLY_FILLED) {
                Right(req.copy(owner = Address.normalize(req.owner)))
              } else {
                Left(ERR_ORDER_VALIDATION_INVALID_CANCELED)
              }
            case Some(_) =>
              Left(ERR_ORDER_VALIDATION_INVALID_OWNER)

            case None =>
              Left(ERR_ORDER_NOT_EXIST)
          }
      }
    }

  }

  private def checkSign(req: CancelOrder.Req): Boolean = {
    val cancelRequest = Map(
      "id" -> req.id,
      "owner" -> req.owner,
      "market" -> req.marketPair
        .map(
          marketPair =>
            NumericConversion.toHexString(MarketHash(marketPair).bigIntValue)
        )
        .getOrElse(""),
      "time" -> NumericConversion.toHexString(req.getTime)
    )
    val message = Map("message" -> cancelRequest)
    val completedMessage = compact(schemaJson merge render(message))
    val typedData = eip712Support.jsonToTypedData(completedMessage)
    val hash = eip712Support.getEIP712Message(typedData)
    val sigBytes = Numeric.hexStringToByteArray(req.sig)
    verifyEthereumSignature(
      Numeric.hexStringToByteArray(hash),
      sigBytes.slice(0, 32),
      sigBytes.slice(32, 64),
      sigBytes(64),
      Address(req.owner)
    )
  }
}
