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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.deployment._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.actors.data._

import scala.concurrent._

object MarketManagerActor {
  val name = "market_manager"
}

class MarketManagerActor(
    marketId: XMarketId,
    config: XMarketManagerConfig
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout,
    timeProvider: TimeProvider,
    tokenValueEstimator: TokenValueEstimator,
    ringIncomeEstimator: RingIncomeEstimator,
    dustOrderEvaluator: DustOrderEvaluator,
    tokenMetadataManager: TokenMetadataManager
)
  extends Actor
  with ActorLogging {

  private implicit val marketId_ = marketId

  private val ringMatcher = new RingMatcherImpl()
  private val pendingRingPool = new PendingRingPoolImpl()
  private val aggregator = new OrderAwareOrderbookAggregatorImpl(
    config.priceDecimals
  )

  private val manager: MarketManager = new MarketManagerImpl(
    marketId,
    config,
    tokenMetadataManager,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator
  )

  private var gasPriceActor: ActorSelection = _
  private var orderHistoryActor: ActorSelection = _
  private var orderbookManagerActor: ActorSelection = _
  private var settlementActor: ActorSelection = _

  def receive: Receive = LoggingReceive {
    case ActorDependencyReady(paths) ⇒
      log.info(s"actor dependency ready: $paths")
      assert(paths.size == 4)
      gasPriceActor = context.actorSelection(paths(0))
      orderHistoryActor = context.actorSelection(paths(1))
      orderbookManagerActor = context.actorSelection(paths(2))
      settlementActor = context.actorSelection(paths(3))
      context.become(functional)
  }

  def functional: Receive = LoggingReceive {

    case XSubmitOrderReq(Some(xorder)) ⇒
      val order: Order = xorder
      xorder.status match {
        case XOrderStatus.NEW | XOrderStatus.PENDING ⇒
          for {
            // get ring settlement cost
            cost ← getCostOfSingleRing()

            // get order fill history
            orderHistoryRes ← (orderHistoryActor ? XGetOrderFilledAmountReq(order.id))
              .mapTo[XGetOrderFilledAmountRes]
            _ = log.debug(s"order history: orderHistoryRes")

            // scale the order
            _order = order.withFilledAmountS(orderHistoryRes.filledAmountS)

            // submit order to reserve balance and allowance
            matchResult = manager.submitOrder(_order, cost)

            // update order book (depth)
            ou = matchResult.orderbookUpdate
            _ = log.debug(ou.toString)
            _ = orderbookManagerActor ! ou if ou.sells.nonEmpty || ou.buys.nonEmpty

            // TODO(hongyu): convert rings to settlement and send it to settlementActor
            rings = matchResult.rings
            _ = log.debug(s"rings: $rings")

          } yield Unit

        case s ⇒
          log.error(s"unexpected order status in XSubmitOrderReq: $s")
          sender ! XSubmitOrderRes(error = XErrorCode.ERR_ORDER_ALREADY_EXIST)
      }

    case XCancelOrderReq(orderId, hardCancel) ⇒
      manager.cancelOrder(orderId)
      sender ! XCancelOrderRes(id = orderId, error = XErrorCode.ERR_OK)

    case updatedGasPrce: XUpdatedGasPrice ⇒
      for {
        cost ← getCostOfSingleRing()
        resultOpt = manager.triggerMatch(true, cost)
      } yield {
        resultOpt foreach { result ⇒
          val ou = result.orderbookUpdate
          if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
            orderbookManagerActor ! ou
          }
        }
      }
  }

  private def getCostOfSingleRing() = for {
    res ← (gasPriceActor ? XGetGasPriceReq())
      .mapTo[XGetGasPriceRes]
    costedEth = BigInt(400000) * BigInt(res.gasPrice)
    //todo:eth的标识符
    cost = tokenValueEstimator.getEstimatedValue("ETH", costedEth)
  } yield cost

}
