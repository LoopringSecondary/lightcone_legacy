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
import io.lightcone.core.ErrorCode._
import io.lightcone.core._
import io.lightcone.ethereum._
import io.lightcone.lib._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data._
import MessageValidator._

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
    dustOrderEvaluator: DustOrderEvaluator,
    eip712Support: EIP712Support,
    dbModule: DatabaseModule)
    extends MessageValidator {

  import MarketMetadata.Status._
  import OrderStatus._

  val selfConfig = config.getConfig(MultiAccountManagerActor.name)
  val numOfShards = selfConfig.getInt("num-of-shards")

  val orderValidator: RawOrderValidator = new RawOrderValidatorImpl
  val cancelOrderValidator: CancelOrderValidator = new CancelOrderValidator()

  def validate = {
    case req: CancelOrder.Req =>
      cancelOrderValidator
        .validate(req)
        .map(_.copy(status = STATUS_SOFT_CANCELLED_BY_USER))

    case req: GetAccounts.Req =>
      Future {
        if (req.addresses.isEmpty || !req.addresses.forall(Address.isValid)) {
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            message = s"invalid addresses in GetAccount.Req:$req"
          )
        } else if (req.allTokens)
          req.copy(
            addresses = req.addresses.map(Address.normalize),
            tokens = (req.tokens union metadataManager
              .getTokens()
              .map(_.getMetadata.address)).distinct.map(normalize)
          )
        else if (req.tokens.nonEmpty)
          req.copy(
            addresses = req.addresses.map(Address.normalize),
            tokens = req.tokens.map(normalize)
          )
        else
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            message = s"invalid tokens in GetAccount.Req:$req"
          )
      }

    case req: GetAccount.Req =>
      Future {
        if (req.address.isEmpty || !Address.isValid(req.address)) {
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            message = s"invalid address in GetAccount.Req:$req"
          )
        } else if (req.allTokens)
          req.copy(
            address = Address.normalize(req.address),
            tokens = (req.tokens.map(normalize) union metadataManager
              .getTokens()
              .map(_.getMetadata.address)).distinct
          )
        else if (req.tokens.nonEmpty)
          req.copy(
            address = Address.normalize(req.address),
            tokens = req.tokens.map(normalize)
          )
        else
          throw ErrorException(
            ERR_INVALID_ARGUMENT,
            message = s"invalid tokens in GetAccount.Req:$req"
          )
      }

    case req: GetAccountNonce.Req =>
      Future {
        req.copy(address = Address.normalize(req.address))
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
            if (dustOrderEvaluator.isOriginalDust(rawOrder.toOrder())) {
              throw ErrorException(
                ERR_ORDER_DUST_VALUE,
                message = s"order value is too little"
              )
            }

            val marketPair = MarketPair(rawOrder.tokenS, rawOrder.tokenB)
            metadataManager.assertMarketStatus(marketPair, ACTIVE)

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
