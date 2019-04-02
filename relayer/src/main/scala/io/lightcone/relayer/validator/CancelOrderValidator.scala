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

  def validate(req: CancelOrder.Req): Future[CancelOrder.Req] = {
    val current = timeProvider.getTimeSeconds()

    if (req.time.isEmpty ||
        NumericConversion.toBigInt(req.getTime) < current - validityInSeconds
        || NumericConversion.toBigInt(req.getTime) > current || (req.id.isEmpty && (req.owner.isEmpty || !Address
          .isValid(req.owner)))) {
      Future {
        throw ErrorException(
          ERR_INVALID_ARGUMENT,
          "invalid cancel order argument"
        )
      }
    }
    (req match {
      case CancelOrder.Req("", owner, _, None, _, _) =>
        Future.successful(req.copy(owner = Address.normalize(owner)))
      case CancelOrder.Req("", owner, _, Some(marketPair), _, _) =>
        Future {
          try {
            metadataManager.isMarketStatus(marketPair, ACTIVE, READONLY)
            req.copy(
              owner = Address.normalize(owner),
              marketPair = Some(marketPair.normalize())
            )
          } catch {
            case _: Throwable =>
              throw ErrorException(ERR_INVALID_MARKET, "unsupported market")
          }
        }

      case CancelOrder.Req(id, _, _, _, _, _) =>
        dbModule.orderService.getOrder(id).map {
          case Some(order) =>
            //TODO(HONGYU,YONGFENG): 订单状态的变迁需要确定规则，另外是否需要在此处过滤
            if (order.getState.status == STATUS_NEW ||
                order.getState.status == STATUS_PENDING ||
                order.getState.status == STATUS_PENDING_ACTIVE) {
              req.copy(owner = Address.normalize(order.owner))
            } else {
              throw ErrorException(
                ERR_ORDER_VALIDATION_INVALID_CANCELED,
                s"order status is ${order.getState.status} so that no need to cancel "
              )
            }
          case None =>
            throw ErrorException(ERR_ORDER_NOT_EXIST, "Order does not exist")
        }
    }).map { newReq =>
      if (validateSign(newReq))
        newReq
      else
        throw ErrorException(
          ERR_ORDER_VALIDATION_INVALID_CANCEL_SIG,
          "invalid sig"
        )
    }
  }

  private def validateSign(req: CancelOrder.Req): Boolean = {
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
