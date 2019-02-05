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
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.core.MetadataManager
import org.loopring.lightcone.ethereum._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib._
import org.loopring.lightcone.core._
import org.loopring.lightcone.persistence.DatabaseModule
import org.loopring.lightcone.core.ErrorCode._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core._

import scala.concurrent._

// Owner: Hongyu

object MultiAccountManagerMessageValidator {
  val name = "multi_account_manager_validator"
}

// This class can be deleted in the future.
final class MultiAccountManagerMessageValidator(
  )(
    implicit
    val config: Config,
    timeProvider: TimeProvider,
    ec: ExecutionContext,
    metadataManager: MetadataManager,
    dbModule: DatabaseModule)
    extends MessageValidator {

  import OrderStatus._

  val selfConfig = config.getConfig(MultiAccountManagerActor.name)
  val numOfShards = selfConfig.getInt("num-of-shards")

  val orderValidator: RawOrderValidator = RawOrderValidatorDefault
  val cancelOrderValidator: CancelOrderValidator = new CancelOrderValidator()

  def normalize(token: String): String = {
    if (metadataManager.hasSymbol(token)) {
      metadataManager.getTokenBySymbol(token).get.meta.address
    } else if (Address.isValid(token)) {
      Address.normalize(token)
    } else {
      throw ErrorException(
        code = ErrorCode.ERR_ETHEREUM_ILLEGAL_ADDRESS,
        message = s"unexpected token $token"
      )
    }
  }

  def validate = {
    case req: CancelOrder.Req =>
      for {
        orderOpt <- dbModule.orderService.getOrder(req.id)
        order = orderOpt.getOrElse(throw ErrorException(ERR_ORDER_NOT_EXIST))
        marketPair = MarketPair(order.tokenS, order.tokenB)
        newReq = req.copy(
          owner = Address.normalize(req.owner),
          status = STATUS_SOFT_CANCELLED_BY_USER,
          marketPair = Some(marketPair.toLowerCase())
        )
        _ <- cancelOrderValidator.validate(newReq)
      } yield newReq

    case req: GetBalanceAndAllowances.Req =>
      Future {
        req.copy(
          address = Address.normalize(req.address),
          tokens = req.tokens.map(normalize)
        )
      }

    case req @ SubmitOrder.Req(Some(order)) =>
      Future {
        orderValidator.validate(order) match {
          case Left(errorCode) =>
            throw ErrorException(
              errorCode,
              message = s"invalid order in SubmitOrder.Req:$order"
            )
          case Right(rawOrder) =>
            val marketPair = MarketPair(rawOrder.tokenS, rawOrder.tokenB)
            metadataManager.assertMarketPairIsActive(marketPair)

            val marketId = MarketHash(marketPair).longId

            val now = timeProvider.getTimeMillis
            val state = RawOrder.State(
              createdAt = now,
              updatedAt = now,
              status = STATUS_NEW
            )

            req.withRawOrder(
              rawOrder.copy(
                hash = rawOrder.hash.toLowerCase(),
                owner = Address.normalize(rawOrder.owner),
                tokenS = Address.normalize(rawOrder.tokenS),
                tokenB = Address.normalize(rawOrder.tokenB),
                params = Some(
                  rawOrder.getParams.copy(
                    dualAuthAddr = rawOrder.getParams.dualAuthAddr.toLowerCase,
                    broker = rawOrder.getParams.broker.toLowerCase(),
                    orderInterceptor =
                      rawOrder.getParams.orderInterceptor.toLowerCase(),
                    wallet = rawOrder.getParams.wallet.toLowerCase()
                  )
                ),
                feeParams =
                  Some(
                    rawOrder.getFeeParams.copy(
                      tokenFee =
                        Address.normalize(rawOrder.getFeeParams.tokenFee),
                      tokenRecipient =
                        rawOrder.getFeeParams.tokenRecipient.toLowerCase()
                    )
                  ),
                state = Some(state),
                marketId = marketId,
                marketEntityId = MarketManagerActor.getEntityId(marketPair),
                accountEntityId = MultiAccountManagerActor
                  .getEntityId(order.owner)
              )
            )
        }
      }
  }

}
