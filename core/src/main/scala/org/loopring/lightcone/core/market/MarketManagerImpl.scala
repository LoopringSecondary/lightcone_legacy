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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.base._

import org.slf4s.Logging
import scala.annotation.tailrec
import scala.collection.mutable.{ Map, SortedSet }

object MarketManagerImpl {
  private def defaultOrdering() = new Ordering[Matchable] {

    def compare(
      a: Matchable,
      b: Matchable) = {
      if (a.rate < b.rate) -1
      else if (a.rate > b.rate) 1
      else if (a.createdAt < b.createdAt) -1
      else if (a.createdAt > b.createdAt) 1
      else a.id compare b.id //在rate和createAt相同时，根据id排序，否则会丢单
    }
  }
}

class MarketManagerImpl(
  val marketId: MarketId,
  val tokenManager: TokenManager,
  val ringMatcher: RingMatcher,
  val pendingRingPool: PendingRingPool,
  val dustOrderEvaluator: DustOrderEvaluator,
  val aggregator: OrderAwareOrderbookAggregator)
  extends MarketManager
  with Logging {

  import MarketManager._
  import MarketManagerImpl._
  import ErrorCode._
  import OrderStatus._

  private implicit val marketId_ = marketId
  private implicit val tm_ = tokenManager
  private implicit val ordering = defaultOrdering()

  private var isLastTakerSell = false
  private var lastPrice: Double = 0

  private[core] val buys = SortedSet.empty[Matchable] // order.tokenS == marketId.primary
  private[core] val sells = SortedSet.empty[Matchable] // order.tokenS == marketId.secondary

  private[core] val orderMap = Map.empty[String, Matchable]
  private[core] val sides =
    Map(marketId.primary -> buys, marketId.secondary -> sells)

  def getNumOfOrders = orderMap.size
  def getNumOfSellOrders = sells.size
  def getNumOfBuyOrders = buys.size

  def getSellOrders(num: Int, skip: Int) =
    sells.drop(skip).take(num).toSeq

  def getBuyOrders(num: Int, skip: Int) =
    buys.drop(skip).take(num).toSeq

  def getOrder(orderId: String) =
    orderMap.get(orderId)

  def submitOrder(
    order: Matchable,
    minFiatValue: Double = 0): MatchResult = this.synchronized {
    removeOrder(order.id, removeFromPendingRingPool = false)
    matchOrders(order, minFiatValue)
  }

  def cancelOrder(orderId: String): Option[Orderbook.Update] =
    this.synchronized {
      getOrder(orderId).map { order =>
        removeOrder(orderId, removeFromPendingRingPool = true)
        aggregator.getOrderbookUpdate()
      }
    }

  // TODO:dongw
  def deletePendingRing(
    ringId: String,
    restoreState: Boolean): Option[Orderbook.Update] =
    this.synchronized {
      if (pendingRingPool.hasRing(ringId)) {
        pendingRingPool.deleteRing(ringId)
        Some(aggregator.getOrderbookUpdate())
      } else None
    }

  def triggerMatch(
    sellOrderAsTaker: Boolean,
    minFiatValue: Double = 0,
    offset: Int = 0): Option[MatchResult] = this.synchronized {
    val side = if (sellOrderAsTaker) sells else buys
    val takerOption = side.drop(offset).headOption
    takerOption.map(submitOrder(_, minFiatValue))
  }

  private[core] def matchOrders(
    order: Matchable,
    minFiatValue: Double): MatchResult = {
    if (dustOrderEvaluator.isOriginalDust(order)) {
      MatchResult(
        Nil,
        order.copy(status = STATUS_DUST_ORDER),
        Orderbook.Update(Nil, Nil))
    } else if (dustOrderEvaluator.isActualDust(order)) {
      MatchResult(
        Nil,
        order.copy(status = STATUS_COMPLETELY_FILLED),
        Orderbook.Update(Nil, Nil))
    } else {
      var taker = order.copy(status = STATUS_PENDING)
      var rings = Seq.empty[MatchableRing]
      var ordersToAddBack = Seq.empty[Matchable]
      var lastPrice: Double = 0

      // The result of this recursive method is to populate
      // `rings` and `ordersToAddBack`.
      @tailrec
      def recursivelyMatchOrders(): Unit = {
        taker = updateOrderMatchable(taker)
        if (dustOrderEvaluator.isMatchableDust(taker)) return

        popBestMakerOrder(taker).map { order =>
          val maker = updateOrderMatchable(order)

          val matchResult =
            if (dustOrderEvaluator.isMatchableDust(maker))
              Left(ERR_MATCHING_INCOME_TOO_SMALL)
            else ringMatcher.matchOrders(taker, maker, minFiatValue)

          log.debug(s"""
                       | \n-- recursive matching (${taker.id} => ${maker.id}) --
                       | [taker]  : $taker,
                       | [maker]  : $maker,
                       | [result] : $matchResult\n\n
                       | """.stripMargin)
          (maker, matchResult)
        } match {
          case None => // no maker to trade with
          case Some((maker, matchResult)) =>
            // we always need to add maker back even if it is STATUS_PENDING-fully-matched.
            ordersToAddBack :+= maker
            matchResult match {
              case Left(
                ERR_MATCHING_ORDERS_NOT_TRADABLE |
                ERR_MATCHING_TAKER_COMPLETELY_FILLED |
                ERR_MATCHING_INVALID_TAKER_ORDER |
                ERR_MATCHING_INVALID_MAKER_ORDER
                ) => // stop recursive matching

              case Left(error) =>
                recursivelyMatchOrders()

              case Right(ring) =>
                isLastTakerSell = (taker.tokenS == marketId.secondary)
                rings :+= ring
                lastPrice = (taker.price + maker.price) / 2
                pendingRingPool.addRing(ring)
                recursivelyMatchOrders()
            }
        }
      }

      recursivelyMatchOrders()

      // we alsways need to add the taker back even if it is STATUS_PENDING-fully-matched.
      ordersToAddBack :+= taker

      // add each skipped maker orders back
      ordersToAddBack.foreach(addOrder)

      val orderbookUpdate = aggregator
        .getOrderbookUpdate()
        .copy(lastPrice = lastPrice)

      MatchResult(rings, taker.resetMatchable, orderbookUpdate)
    }
  }

  // TODO(dongw)
  def getMetadata() =
    MarketMetadata(
      numBuys = buys.size,
      numSells = sells.size,
      numHiddenBuys = 0,
      numHiddenSells = 0,
      bestBuyPrice = 0.0,
      bestSellPrice = 0.0,
      lastPrice = 0.0,
      isLastTakerSell = isLastTakerSell)

  // Add an order to its side.
  private def addOrder(order: Matchable) {
    // always make sure _matchable is None.
    aggregator.addOrder(order)
    orderMap += order.id -> order
    sides(order.tokenS) += order
  }

  // Remove an order from depths, order map, and its side.
  private def removeOrder(
    orderId: String,
    removeFromPendingRingPool: Boolean): Option[Matchable] = {
    orderMap.get(orderId).map { order =>
      aggregator.deleteOrder(order)
      orderMap -= order.id
      sides(order.tokenS) -= order

      if (removeFromPendingRingPool) {
        pendingRingPool.deleteOrder(orderId)
      }
      order
    }
  }

  // Remove and return the top taker order for a taker order.
  private def popBestMakerOrder(order: Matchable): Option[Matchable] = {
    val side = sides(order.tokenB)
    side.headOption match {
      case None => None
      case Some(order) => removeOrder(order.id, false)
    }
  }

  private[core] def updateOrderMatchable(order: Matchable): Matchable = {

    val pendingAmountS = pendingRingPool.getOrderPendingAmountS(order.id)

    val matchableAmountS = (order.actual.amountS - pendingAmountS).max(0)
    val scale = Rational(matchableAmountS, order.original.amountS)
    order.copy(_matchable = Some(order.original.scaleBy(scale)))
  }
}
