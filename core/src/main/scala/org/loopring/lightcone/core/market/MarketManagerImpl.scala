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
import scala.collection.mutable.{Map, SortedSet}

object MarketManagerImpl {
  private def defaultOrdering() = new Ordering[Matchable] {

    def compare(
        a: Matchable,
        b: Matchable
      ) = {
      if (a.rate < b.rate) -1
      else if (a.rate > b.rate) 1
      else if (a.createdAt < b.createdAt) -1
      else if (a.createdAt > b.createdAt) 1
      else a.id compare b.id //在rate和createAt相同时，根据id排序，否则会丢单
    }
  }
}

class MarketManagerImpl(
    val marketId: XMarketId,
    val tokenManager: TokenManager,
    val ringMatcher: RingMatcher,
    val pendingRingPool: PendingRingPool,
    val dustOrderEvaluator: DustOrderEvaluator,
    val aggregator: OrderAwareOrderbookAggregator)
    extends MarketManager
    with Logging {

  import MarketManager._
  import MarketManagerImpl._
  import XErrorCode._
  import XOrderStatus._

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

  def getSellOrders(
      num: Int,
      returnMatchableAmounts: Boolean = false
    ) = {
    val orders = sells.take(num).toSeq
    if (!returnMatchableAmounts) orders
    else orders.map(updateOrderMatchable)
  }

  def getBuyOrders(
      num: Int,
      returnMatchableAmounts: Boolean = false
    ) = {
    val orders = buys.take(num).toSeq
    if (!returnMatchableAmounts) orders
    else orders.map(updateOrderMatchable)
  }

  def getOrder(
      orderId: String,
      returnMatchableAmounts: Boolean = false
    ) = {
    val order = orderMap.get(orderId)
    if (!returnMatchableAmounts) order
    else order.map(updateOrderMatchable)
  }

  def submitOrder(
      order: Matchable,
      minFiatValue: Double = 0
    ): MatchResult = this.synchronized {
    // Allow re-submission of an existing order.
    removeFromSide(order.id)
    matchOrders(order, minFiatValue)
  }

  def cancelOrder(orderId: String): Option[XOrderbookUpdate] =
    this.synchronized {
      getOrder(orderId).map { order =>
        removeFromSide(orderId)
        pendingRingPool.deleteOrder(orderId)
        aggregator.getOrderbookUpdate()
      }
    }

  def deletePendingRing(ringId: String): Option[XOrderbookUpdate] =
    this.synchronized {
      if (pendingRingPool.hasRing(ringId)) {
        pendingRingPool.deleteRing(ringId)
        Some(aggregator.getOrderbookUpdate())
      } else None
    }

  def triggerMatch(
      sellOrderAsTaker: Boolean,
      minFiatValue: Double = 0,
      offset: Int = 0
    ): Option[MatchResult] = this.synchronized {
    val side = if (sellOrderAsTaker) sells else buys
    val takerOption = side.drop(offset).headOption
    takerOption.map(submitOrder(_, minFiatValue))
  }

  private[core] def matchOrders(
      order: Matchable,
      minFiatValue: Double
    ): MatchResult = {
    if (dustOrderEvaluator.isOriginalDust(order)) {
      MatchResult(
        Nil,
        order.copy(status = STATUS_DUST_ORDER),
        XOrderbookUpdate(Nil, Nil)
      )
    } else if (dustOrderEvaluator.isActualDust(order)) {
      MatchResult(
        Nil,
        order.copy(status = STATUS_COMPLETELY_FILLED),
        XOrderbookUpdate(Nil, Nil)
      )
    } else {
      var taker = order.copy(status = STATUS_PENDING)
      var rings = Seq.empty[OrderRing]
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
          case None                       => // to maker to trade with
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
                lastPrice = (taker.displayablePrice + maker.displayablePrice) / 2
                pendingRingPool.addRing(ring)
                recursivelyMatchOrders()
            }
        }
      }

      recursivelyMatchOrders()

      // we alsways need to add the taker back even if it is STATUS_PENDING-fully-matched.
      ordersToAddBack :+= taker

      // add each skipped maker orders back
      ordersToAddBack.map(_.resetMatchable).foreach(addToSide)

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
      isLastTakerSell = isLastTakerSell
    )

  // Add an order to its side.
  private def addToSide(order: Matchable) {
    // always make sure _matchable is None.
    val order_ = order.copy(_matchable = None)
    aggregator.addOrder(order)
    orderMap += order.id -> order_
    sides(order.tokenS) += order_
  }

  private def removeFromSide(orderId: String) {
    orderMap.get(orderId) match {
      case None =>
      case Some(order) =>
        aggregator.deleteOrder(order)
        orderMap -= order.id
        sides(order.tokenS) -= order
    }
  }

  // Remove and return the top taker order for a taker order.
  private def popBestMakerOrder(order: Matchable): Option[Matchable] =
    popOrder(sides(order.tokenB))

  // Remove and return the top order from one side.
  private def popOrder(side: SortedSet[Matchable]): Option[Matchable] = {
    side.headOption.map { order =>
      aggregator.deleteOrder(order)
      orderMap -= order.id
      side -= order
      order
    }
  }

  private[core] def updateOrderMatchable(order: Matchable): Matchable = {

    val pendingAmountS = pendingRingPool.getOrderPendingAmountS(order.id)

    val matchableAmountS = (order.actual.amountS - pendingAmountS).max(0)
    val scale = Rational(matchableAmountS, order.original.amountS)
    val copy = order.copy(_matchable = Some(order.original.scaleBy(scale)))
    // println(s"""
    //   original: $order
    //   pendingAmountS: $pendingAmountS
    //   actualAmount: ${order.actual}
    //   matchableAmountS: $matchableAmountS
    //   scale: $scale
    //   new : $copy""")
    copy
  }
}
