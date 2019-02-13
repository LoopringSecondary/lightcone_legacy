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

package io.lightcone.core

import org.slf4s.Logging
import scala.annotation.tailrec
import scala.collection.mutable.{Map, SortedSet}
import spire.math.Rational

object MarketManagerImpl {
  private def defaultOrdering() = new Ordering[Matchable] {

    def compare(
        a: Matchable,
        b: Matchable
      ) = {
      if (a.rate < b.rate) -1
      else if (a.rate > b.rate) 1
      else if (a.submittedAt < b.submittedAt) -1
      else if (a.submittedAt > b.submittedAt) 1
      else a.id compare b.id //在rate和createAt相同时，根据id排序，否则会丢单
    }
  }
}

// This class is not thread safe.
class MarketManagerImpl(
    val marketPair: MarketPair,
    val metadataManager: MetadataManager,
    val ringMatcher: RingMatcher,
    val pendingRingPool: PendingRingPool,
    val dustOrderEvaluator: DustOrderEvaluator,
    val aggregator: OrderAwareOrderbookAggregator,
    val maxSettementFailuresPerOrder: Int)
    extends MarketManager
    with Logging {

  import MarketManager._
  import MarketManagerImpl._
  import ErrorCode._
  import OrderStatus._

  private implicit val marketPair_ = marketPair
  private implicit val mm_ = metadataManager
  private implicit val ordering = defaultOrdering()

  private var isLastTakerSell = false
  private var latestPrice: Double = 0
  private var minRequiredIncome: Double = 0

  private[core] val buys = SortedSet.empty[Matchable] // order.tokenS == marketPair.quoteToken
  private[core] val sells = SortedSet.empty[Matchable] // order.tokenS == marketPair.baseToken

  private[core] val orderMap = Map.empty[String, Matchable]
  private[core] val sides =
    Map(marketPair.baseToken -> sells, marketPair.quoteToken -> buys)

  def getNumOfOrders = orderMap.size
  def getNumOfSellOrders = sells.size
  def getNumOfBuyOrders = buys.size

  def getSellOrders(
      num: Int,
      skip: Int
    ) =
    sells.drop(skip).take(num).toSeq

  def getBuyOrders(
      num: Int,
      skip: Int
    ) =
    buys.drop(skip).take(num).toSeq

  def getOrder(orderId: String) =
    orderMap.get(orderId)

  // If an order is in one or more pending rings, that
  // part of the order will not be cancelled.
  def cancelOrder(orderId: String): Option[Orderbook.Update] =
    removeOrder(orderId) map { _ =>
      aggregator.getOrderbookUpdate()
    }

  def deleteRing(
      ringId: String,
      ringSettledSuccessfully: Boolean
    ): Seq[MatchResult] = {
    val orderIds = pendingRingPool.deleteRing(ringId)
    if (ringSettledSuccessfully) Nil
    else resubmitOrders(orderIds)
  }

  def deleteRingsBefore(timestamp: Long): Seq[MatchResult] =
    resubmitOrders(pendingRingPool.deleteRingsBefore(timestamp))

  def deleteRingsOlderThan(ageInSeconds: Long): Seq[MatchResult] =
    resubmitOrders(pendingRingPool.deleteRingsOlderThan(ageInSeconds))

  def submitOrder(
      order: Matchable,
      minRequiredIncome: Double
    ): MatchResult = {
    this.minRequiredIncome = minRequiredIncome
    matchOrders(order, minRequiredIncome)
  }

  def triggerMatch(
      sellOrderAsTaker: Boolean,
      minFiatValue: Double = 0,
      offset: Int = 0
    ): Option[MatchResult] = {
    val side = if (sellOrderAsTaker) sells else buys
    val takerOption = side.drop(offset).headOption
    takerOption.map(submitOrder(_, minFiatValue))
  }

  private[core] def matchOrders(
      order: Matchable,
      minRequiredIncome: Double
    ): MatchResult = {
    if (order.numAttempts > maxSettementFailuresPerOrder) {
      // TODO(dongw): 是否发消息给AccountManager了？
      MatchResult(
        order.copy(status = STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES)
      )
    } else if (dustOrderEvaluator.isOriginalDust(order)) {
      MatchResult(order.copy(status = STATUS_DUST_ORDER))
    } else if (dustOrderEvaluator.isActualDust(order)) {
      MatchResult(order.copy(status = STATUS_COMPLETELY_FILLED))
    } else {
      // 不能removeOrder，因为深度是现有订单减去pending ring中的订单的，因此深度应该是正确的。
      removeOrder(order.id)
      var taker = updateOrderMatchable(order).copy(status = STATUS_PENDING)
      var rings = Seq.empty[MatchableRing]
      var ordersToAddBack = Seq.empty[Matchable]

      // The result of this recursive method is to populate
      // `rings` and `ordersToAddBack`.
      @tailrec
      def recursivelyMatchOrders(): Unit = {
        popTopMakerOrder(taker).map { maker =>
          val matchResult =
            ringMatcher.matchOrders(taker, maker, minRequiredIncome)

          log.debug(s"""
                       | \n-- recursive matching (${taker.id} => ${maker.id}) --
                       | [taker]  : $taker,
                       | [maker]  : $maker,
                       | [result] : $matchResult\n\n
                       | """.stripMargin)
          (maker, matchResult)
        } match {
          case Some(
              (
                maker,
                error @ Left(
                  ERR_MATCHING_ORDERS_NOT_TRADABLE |
                  ERR_MATCHING_TAKER_COMPLETELY_FILLED |
                  ERR_MATCHING_INVALID_TAKER_ORDER |
                  ERR_MATCHING_INVALID_MAKER_ORDER
                )
              )
              ) =>
            log.debug(s"match error: $error")
            ordersToAddBack :+= maker

          case Some((maker, Left(error))) =>
            log.debug(s"match error: $error")
            ordersToAddBack :+= maker
            recursivelyMatchOrders()

          case Some((maker, Right(ring))) =>
            isLastTakerSell = (taker.tokenS == marketPair.baseToken)
            rings :+= ring
            latestPrice = (taker.price + maker.price) / 2

            pendingRingPool.addRing(ring)

            ordersToAddBack :+= updateOrderMatchable(maker)
            taker = updateOrderMatchable(taker)

            if (!dustOrderEvaluator.isMatchableDust(taker)) {
              recursivelyMatchOrders()
            }

          case None =>
            log.debug("no maker found")
        }
      }

      if (!dustOrderEvaluator.isMatchableDust(taker)) {
        recursivelyMatchOrders()
      }

      // we alsways need to add the taker back even if it is pending fully-matched
      ordersToAddBack :+= taker

      ordersToAddBack.foreach(addOrder)

      val orderbookUpdate = aggregator
        .getOrderbookUpdate()
        .copy(latestPrice = latestPrice)

      // println("MMI: orderbookUpdate: " + orderbookUpdate)
      MatchResult(taker, rings, orderbookUpdate)
    }
  }

  def getStats() =
    MarketStats(
      numBuys = buys.size,
      numSells = sells.size,
      numOrders = orderMap.size,
      bestBuyPrice = buys.headOption.map(_.price).getOrElse(0),
      bestSellPrice = sells.headOption.map(_.price).getOrElse(0),
      latestPrice = latestPrice,
      isLastTakerSell = isLastTakerSell
    )

  // Add an order to its side.
  private def addOrder(order: Matchable): Unit = {
    assert(order._actual.isDefined)
    assert(order._matchable.isDefined)

    orderMap += order.id -> order

    if (!dustOrderEvaluator.isMatchableDust(order)) {
      sides(order.tokenS) += order
      aggregator.addOrder(order)
    }
  }

  // Remove an order from depths, order map, and its side.
  private def removeOrder(orderId: String): Option[Matchable] = {
    orderMap.get(orderId).map { order =>
      orderMap -= orderId
      aggregator.deleteOrder(order)
      sides(order.tokenS) -= order
      order
    }
  }

  // Remove and return the top taker order for a taker order.
  private def popTopMakerOrder(order: Matchable): Option[Matchable] = {
    val side = sides(order.tokenB)
    side.headOption match {
      case None        => None
      case Some(order) => removeOrder(order.id)
    }
  }

  private def updateOrderMatchable(order: Matchable): Matchable = {
    val pendingAmountS = pendingRingPool.getOrderPendingAmountS(order.id)
    val matchableBaseAmountS = (order.actual.amountS - pendingAmountS).max(0)
    val scale = Rational(matchableBaseAmountS, order.original.amountS)
    order.copy(_matchable = Some(order.original.scaleBy(scale)))
  }

  private def resubmitOrders(orderIds: Set[String]): Seq[MatchResult] = {
    orderIds.toSeq
      .map(orderMap.get)
      .filter(_.isDefined)
      .map(_.get)
      .map { order =>
        order.copy(numAttempts = order.numAttempts + 1)
      }
      .sortWith(_.submittedAt < _.submittedAt)
      .map(submitOrder(_, minRequiredIncome))
  }

  def getOrderbookSlots(num: Int): Orderbook.Update =
    aggregator.getOrderbookSlots(num)
}
