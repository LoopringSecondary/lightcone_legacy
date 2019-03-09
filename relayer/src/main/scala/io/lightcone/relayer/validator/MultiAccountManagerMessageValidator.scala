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
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data._
import io.lightcone.core.MetadataManager
import io.lightcone.ethereum._
import io.lightcone.lib._
import io.lightcone.core._
import io.lightcone.persistence.DatabaseModule
import io.lightcone.core.ErrorCode._
import io.lightcone.relayer.data._
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
  import MarketMetadata.Status._

  val selfConfig = config.getConfig(MultiAccountManagerActor.name)
  val numOfShards = selfConfig.getInt("num-of-shards")

  val orderValidator: RawOrderValidator = new RawOrderValidatorImpl
  val cancelOrderValidator: CancelOrderValidator = new CancelOrderValidator()

  def normalize(addrOrSymbol: String): String = {
    metadataManager.getTokenWithSymbol(addrOrSymbol) match {
      case Some(t) =>
        t.getAddress()
      case None if Address.isValid(addrOrSymbol) =>
        Address.normalize(addrOrSymbol)
      case _ =>
        throw ErrorException(
          code = ErrorCode.ERR_ETHEREUM_ILLEGAL_ADDRESS,
          message = s"invalid address or symbol $addrOrSymbol"
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
          marketPair = Some(marketPair.normalize)
        )
        _ <- cancelOrderValidator.validate(newReq)
      } yield newReq

    case req: GetAccounts.Req =>
      Future {
        req.copy(
          addresses = req.addresses.map(Address.normalize),
          tokens = req.tokens.map(normalize)
        )
      }

    case req: GetAccount.Req =>
      Future {
        req.copy(
          address = Address.normalize(req.address),
          tokens = req.tokens.map(normalize)
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
            val marketPair = MarketPair(rawOrder.tokenS, rawOrder.tokenB)
            println(
              s"##### rawOrder ${rawOrder.tokenS}, ${rawOrder.tokenB}, ${metadataManager.getMarkets()}"
            )
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
