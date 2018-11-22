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
import akka.pattern.ask
import akka.util.Timeout
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.data.Order
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market.MarketManager.MatchResult
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment._

import scala.concurrent._

object MarketManagerActor {
  val name = "market_manager"
  val wethTokenAddress = "WETH" // TODO
}

// TODO(hongyu): schedule periodical job to send self a XTriggerRematchReq message.
class MarketManagerActor(
    val marketId: XMarketId,
    val config: XMarketManagerConfig,
    val skipRecovery: Boolean = false
)(
    implicit
    val ec: ExecutionContext,
    val timeout: Timeout,
    val timeProvider: TimeProvider,
    val tokenValueEstimator: TokenValueEstimator,
    val ringIncomeEstimator: RingIncomeEstimator,
    val dustOrderEvaluator: DustOrderEvaluator,
    val tokenMetadataManager: TokenMetadataManager
)
  extends Actor
  with ActorLogging
  with OrderRecoverySupport {

  val ownerOfOrders = None
  val recoverBatchSize = config.recoverBatchSize

  private val GAS_LIMIT_PER_RING_IN_LOOPRING_V2 = BigInt(400000)

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

  protected var orderDatabaseAccessActor: ActorSelection = _
  protected var gasPriceActor: ActorSelection = _
  protected var orderbookManagerActor: ActorSelection = _
  protected var settlementActor: ActorSelection = _

  def receive: Receive = LoggingReceive {

    case XActorDependencyReady(paths) ⇒
      log.info(s"actor dependency ready: $paths")
      assert(paths.size == 4)
      orderDatabaseAccessActor = context.actorSelection(paths(0))
      gasPriceActor = context.actorSelection(paths(1))
      orderbookManagerActor = context.actorSelection(paths(2))
      settlementActor = context.actorSelection(paths(3))

      startOrderRecovery()
  }

  def functional: Receive = functionalBase orElse LoggingReceive {

    case XSubmitOrderReq(Some(xorder)) ⇒
      submitOrder(xorder)

    case XCancelOrderReq(orderId, hardCancel) ⇒
      manager.cancelOrder(orderId) foreach {
        orderUpdate ⇒ orderbookManagerActor ! orderUpdate
      }
      sender ! XCancelOrderRes(id = orderId)

    case XGasPriceUpdated(_gasPrice) ⇒
      val gasPrice: BigInt = _gasPrice
      manager.triggerMatch(true, getRequiredMinimalIncome(gasPrice)) foreach {
        matchResult ⇒
          updateOrderbookAndSettleRings(matchResult, gasPrice)
      }

    case XTriggerRematchReq(sellOrderAsTaker, offset) ⇒ for {
      res ← (gasPriceActor ? XGetGasPriceReq()).mapTo[XGetGasPriceRes]
      gasPrice: BigInt = res.gasPrice
      minRequiredIncome = getRequiredMinimalIncome(gasPrice)
      _ = manager.triggerMatch(sellOrderAsTaker, minRequiredIncome, offset)
        .foreach { updateOrderbookAndSettleRings(_, gasPrice) }
    } yield Unit

  }

  private def submitOrder(xorder: XOrder): Future[Unit] = {
    assert(xorder.actual.nonEmpty, "order in XSubmitOrderReq miss `actual` field")
    val order: Order = xorder
    xorder.status match {
      case XOrderStatus.NEW | XOrderStatus.PENDING ⇒ for {
        // get ring settlement cost
        res ← (gasPriceActor ? XGetGasPriceReq()).mapTo[XGetGasPriceRes]

        gasPrice: BigInt = res.gasPrice
        minRequiredIncome = getRequiredMinimalIncome(gasPrice)

        _ = println(".............+ " + minRequiredIncome)

        // submit order to reserve balance and allowance
        matchResult = manager.submitOrder(order, minRequiredIncome)
        _ = println(".....||" + matchResult)

        //settlement matchResult and update orderbook
        _ = updateOrderbookAndSettleRings(matchResult, gasPrice)
      } yield Unit

      case s ⇒
        log.error(s"unexpected order status in XSubmitOrderReq: $s")
        Future.successful(Unit)
    }
  }

  private def getRequiredMinimalIncome(gasPrice: BigInt): Double = {
    val costinEth = GAS_LIMIT_PER_RING_IN_LOOPRING_V2 * gasPrice
    tokenValueEstimator.getEstimatedValue(MarketManagerActor.wethTokenAddress, costinEth)
  }

  private def updateOrderbookAndSettleRings(matchResult: MatchResult, gasPrice: BigInt) {
    // Update order book (depth)
    val ou = matchResult.orderbookUpdate
    println("________--1" + ou)
    if (ou.sells.nonEmpty || ou.buys.nonEmpty) {
      orderbookManagerActor ! ou
    }

    // Settle rings
    if (matchResult.rings.nonEmpty) {
      log.debug(s"rings: ${matchResult.rings}")

      settlementActor ! XSettleRingsReq(
        rings = matchResult.rings,
        gasLimit = GAS_LIMIT_PER_RING_IN_LOOPRING_V2 * matchResult.rings.size,
        gasPrice = gasPrice
      )
    }
  }

  protected def recoverOrder(xorder: XOrder): Future[Any] = submitOrder(xorder)
}
