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
import akka.pattern._
import akka.util.Timeout
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.deployment._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.actors.data._

import scala.concurrent.{ ExecutionContext, Future }

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
  private var orderbookManagerActor: ActorSelection = _

  def receive: Receive = LoggingReceive {
    case ActorDependencyReady(paths) ⇒
      log.info(s"actor dependency ready: $paths")
      assert(paths.size == 2)
      gasPriceActor = context.actorSelection(paths(0))
      orderbookManagerActor = context.actorSelection(paths(1))
      context.become(functional)
  }

  def functional: Receive = LoggingReceive {

    // TODO(hongyu): send a response to the sender
    case XSubmitOrderReq(Some(order)) ⇒
      order.status match {
        case XOrderStatus.NEW | XOrderStatus.PENDING ⇒ for {
          cost ← getCostOfSingleRing()
          res = manager.submitOrder(order, cost)
          ou = res.orderbookUpdate
        } yield {
          if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
            orderbookManagerActor ! ou
          }
        }

        case s ⇒
          log.error(s"unexpected order status in XSubmitOrderReq: $s")
      }

    case XCancelOrderReq(orderId, hardCancel) ⇒
      manager.cancelOrder(orderId)

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
