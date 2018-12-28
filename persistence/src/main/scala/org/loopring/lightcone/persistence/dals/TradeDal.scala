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

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

trait TradeDal extends BaseDalImpl[TradeTable, XTrade] {
  def saveTrade(trade: XTrade): Future[Either[ErrorCode, String]]
  def saveTrades(trades: Seq[XTrade]): Future[Seq[Either[ErrorCode, String]]]
  def getTrades(request: GetTradesReq): Future[Seq[XTrade]]
  def countTrades(request: GetTradesReq): Future[Int]
  def obsolete(height: Long): Future[Unit]
}

class TradeDalImpl(
  )(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends TradeDal {
  val query = TableQuery[TradeTable]
  val timeProvider = new SystemTimeProvider()
  private[this] val logger = Logger(this.getClass)

  def saveTrade(trade: XTrade): Future[Either[ErrorCode, String]] = {
    db.run(
        (query += trade.copy(
          marketHash =
            MarketHashProvider.convert2Hex(trade.tokenS, trade.tokenB),
          createdAt = timeProvider.getTimeSeconds()
        )).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) ⇒
          Left(ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT)
        case Failure(ex) ⇒ {
          logger.error(s"error : ${ex.getMessage}")
          Left(ErrorCode.ERR_PERSISTENCE_INTERNAL)
        }
        case Success(x) ⇒ Right(trade.txHash)
      }
  }

  def saveTrades(trades: Seq[XTrade]): Future[Seq[Either[ErrorCode, String]]] =
    Future.sequence(trades.map(saveTrade))

  private def queryFilters(
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[TradeTable, TradeTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (owner.nonEmpty) filters = filters.filter(_.owner === owner.get)
    if (tokenS.nonEmpty) filters = filters.filter(_.tokenS === tokenS.get)
    if (tokenB.nonEmpty) filters = filters.filter(_.tokenB === tokenB.get)
    if (marketHash.nonEmpty)
      filters = filters.filter(_.marketHash === marketHash.get)
    if (sort.nonEmpty) filters = sort.get match {
      case SortingType.ASC ⇒ filters.sortBy(_.sequenceId.asc)
      case SortingType.DESC ⇒ filters.sortBy(_.sequenceId.desc)
      case _ ⇒ filters.sortBy(_.sequenceId.asc)
    }
    filters = pagingOpt match {
      case Some(paging) ⇒ filters.drop(paging.skip).take(paging.size)
      case None ⇒ filters
    }
    filters
  }

  def getTrades(request: GetTradesReq): Future[Seq[XTrade]] = {
    val owner = if (request.owner.isEmpty) None else Some(request.owner)
    val (tokenS, tokenB, marketHash) = request.market match {
      case GetTradesReq.Market.MarketHash(v) ⇒ (None, None, Some(v))
      case GetTradesReq.Market.Pair(v) ⇒ (Some(v.tokenS), Some(v.tokenB), None)
      case _ ⇒ (None, None, None)
    }
    val filters = queryFilters(
      owner,
      tokenS,
      tokenB,
      marketHash,
      Some(request.sort),
      request.skip
    )
    db.run(filters.result)
  }

  def countTrades(request: GetTradesReq): Future[Int] = {
    val owner = if (request.owner.isEmpty) None else Some(request.owner)
    val (tokenS, tokenB, marketHash) = request.market match {
      case GetTradesReq.Market.MarketHash(v) ⇒ (None, None, Some(v))
      case GetTradesReq.Market.Pair(v) ⇒ (Some(v.tokenS), Some(v.tokenB), None)
      case _ ⇒ (None, None, None)
    }
    val filters = queryFilters(
      owner,
      tokenS,
      tokenB,
      marketHash,
      Some(request.sort),
      request.skip
    )
    db.run(filters.size.result)
  }

  def obsolete(height: Long): Future[Unit] = {
    db.run(query.filter(_.blockHeight >= height).delete).map(_ >= 0)
  }
}
