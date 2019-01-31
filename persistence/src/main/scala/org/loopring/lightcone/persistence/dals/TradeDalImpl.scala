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

package org.loopring.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import org.loopring.lightcone.lib._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.GetTrades._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

class TradeDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-trade") val dbConfig: DatabaseConfig[JdbcProfile],
    timeProvider: TimeProvider)
    extends TradeDal {
  val query = TableQuery[TradeTable]

  def saveTrade(trade: Trade): Future[ErrorCode] = {
    db.run(
        (query += trade.copy(
          marketKey = MarketKey(trade.tokenS, trade.tokenB).toString
        )).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) =>
          ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT
        case Failure(ex) => {
          logger.error(s"error : ${ex.getMessage}")
          ErrorCode.ERR_PERSISTENCE_INTERNAL
        }
        case Success(x) => ErrorCode.ERR_NONE
      }
  }

  def saveTrades(trades: Seq[Trade]): Future[Seq[ErrorCode]] =
    Future.sequence(trades.map(saveTrade))

  def getTrades(request: Req): Future[Seq[Trade]] = {
    val (tokensOpt, tokenbOpt, marketKeyOpt) = getMarketQueryParameters(
      request.market
    )
    val (ringHashOpt, ringIndexOpt, fillIndexOpt) = getRingQueryParameters(
      request.ring
    )
    val filters = queryFilters(
      getOptString(request.owner),
      getOptString(request.txHash),
      getOptString(request.orderHash),
      ringHashOpt,
      ringIndexOpt,
      fillIndexOpt,
      tokensOpt,
      tokenbOpt,
      marketKeyOpt,
      getOptString(request.wallet),
      getOptString(request.miner),
      Some(request.sort),
      request.skip
    )
    db.run(filters.result)
  }

  def countTrades(request: Req): Future[Int] = {
    val (tokensOpt, tokenbOpt, marketKeyOpt) = getMarketQueryParameters(
      request.market
    )
    val (ringHashOpt, ringIndexOpt, fillIndexOpt) = getRingQueryParameters(
      request.ring
    )
    val filters = queryFilters(
      getOptString(request.owner),
      getOptString(request.txHash),
      getOptString(request.orderHash),
      ringHashOpt,
      ringIndexOpt,
      fillIndexOpt,
      tokensOpt,
      tokenbOpt,
      marketKeyOpt,
      getOptString(request.wallet),
      getOptString(request.miner),
      None,
      None
    )
    db.run(filters.size.result)
  }

  def obsolete(height: Long): Future[Unit] = {
    db.run(query.filter(_.blockHeight >= height).delete).map(_ >= 0)
  }

  private def getOptString(str: String) = {
    if (str.nonEmpty) Some(str) else None
  }

  private def queryFilters(
      owner: Option[String] = None,
      txHash: Option[String] = None,
      orderHash: Option[String] = None,
      ringHash: Option[String] = None,
      ringIndex: Option[Long] = None,
      fillIndex: Option[Int] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketKey: Option[String] = None,
      wallet: Option[String] = None,
      miner: Option[String] = None,
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[TradeTable, TradeTable#TableElementType, Seq] = {
    var filters = query.filter(_.ringIndex >= 0L)
    if (owner.nonEmpty) filters = filters.filter(_.owner === owner.get)
    if (txHash.nonEmpty) filters = filters.filter(_.txHash === txHash.get)
    if (orderHash.nonEmpty)
      filters = filters.filter(_.orderHash === orderHash.get)
    if (ringHash.nonEmpty) filters = filters.filter(_.ringHash === ringHash.get)
    if (ringIndex.nonEmpty)
      filters = filters.filter(_.ringIndex === ringIndex.get)
    if (fillIndex.nonEmpty)
      filters = filters.filter(_.fillIndex === fillIndex.get)
    if (tokenS.nonEmpty) filters = filters.filter(_.tokenS === tokenS.get)
    if (tokenB.nonEmpty) filters = filters.filter(_.tokenB === tokenB.get)
    if (marketKey.nonEmpty)
      filters = filters.filter(_.marketKey === marketKey.get)
    if (wallet.nonEmpty) filters = filters.filter(_.wallet === wallet.get)
    if (miner.nonEmpty) filters = filters.filter(_.miner === miner.get)
    filters = sort match {
      case Some(s) if s == SortingType.DESC =>
        filters.sortBy(c => (c.ringIndex.desc, c.fillIndex.desc))
      case _ => filters.sortBy(c => (c.ringIndex.asc, c.fillIndex.asc))
    }
    filters = pagingOpt match {
      case Some(paging) => filters.drop(paging.skip).take(paging.size)
      case None         => filters
    }
    filters
  }

  private def getMarketQueryParameters(marketOpt: Option[Req.Market]) = {
    marketOpt match {
      case Some(m)
          if m.tokenS.nonEmpty && m.tokenB.nonEmpty && m.isQueryBothSide =>
        (None, None, Some(MarketKey(m.tokenS, m.tokenB).toString))
      case Some(m) if m.tokenS.nonEmpty && m.tokenB.nonEmpty =>
        (Some(m.tokenS), Some(m.tokenB), None)
      case Some(m) if m.tokenS.nonEmpty => (Some(m.tokenS), None, None)
      case Some(m) if m.tokenB.nonEmpty => (None, Some(m.tokenB), None)
      case None                         => (None, None, None)
    }
  }

  private def getRingQueryParameters(ringOpt: Option[Req.Ring]) = {
    ringOpt match {
      case Some(r) =>
        val ringHash = getOptString(r.ringHash)
        val ringIndex =
          if (r.ringIndex.nonEmpty) Some(r.ringIndex.toLong) else None
        val fillIndex =
          if (r.fillIndex.nonEmpty) Some(r.fillIndex.toInt) else None
        (ringHash, ringIndex, fillIndex)
      case None => (None, None, None)
    }
  }
}
