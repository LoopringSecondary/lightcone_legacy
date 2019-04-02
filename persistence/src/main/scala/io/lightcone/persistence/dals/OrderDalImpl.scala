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

package io.lightcone.persistence.dals

import com.google.inject.Inject
import com.google.inject.name.Named
import com.mysql.jdbc.exceptions.jdbc4._
import io.lightcone.lib._
import io.lightcone.persistence.base._
import io.lightcone.persistence._
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc._
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

class OrderDalImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    @Named("dbconfig-dal-order") val dbConfig: DatabaseConfig[JdbcProfile],
    timeProvider: TimeProvider)
    extends OrderDal {

  import ErrorCode._

  val query = TableQuery[OrderTable]
  def getRowHash(row: RawOrder) = row.hash
  implicit val OrderStatusColumnType = enumColumnType(OrderStatus)
  implicit val TokenStandardColumnType = enumColumnType(TokenStandard)

  val failedStatus = Seq(
    OrderStatus.STATUS_SOFT_CANCELLED_BY_USER,
    OrderStatus.STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR,
    OrderStatus.STATUS_ONCHAIN_CANCELLED_BY_USER,
    OrderStatus.STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR,
    OrderStatus.STATUS_SOFT_CANCELLED_LOW_BALANCE,
    OrderStatus.STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE,
    OrderStatus.STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS,
    OrderStatus.STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES
  )

  val activeStatus = Set(
    OrderStatus.STATUS_NEW,
    OrderStatus.STATUS_PENDING,
    OrderStatus.STATUS_PARTIALLY_FILLED
  )

  def saveOrder(
      order: RawOrder
    ): Future[(ErrorCode, Option[RawOrder], Boolean)] = {
    db.run((query += order).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) => {
        (
          ERR_PERSISTENCE_DUPLICATE_INSERT,
          None,
          true
        )
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        (ERR_PERSISTENCE_INTERNAL, None, false)
      }
      case Success(x) =>
        (ERR_NONE, Some(order), false)
    }
  }

  def getOrders(hashes: Seq[String]): Future[Seq[RawOrder]] = {
    if (hashes.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      db.run(query.filter(_.hash inSet hashes).result)
    }
  }

  def getOrdersMap(hashes: Seq[String]): Future[Map[String, RawOrder]] =
    getOrders(hashes).map(_.map(r => r.hash -> r).toMap)

  def getOrder(hash: String): Future[Option[RawOrder]] =
    db.run(query.filter(_.hash === hash).result.headOption)

  private def queryOrderFilters(
      statuses: Set[OrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashes: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      validTime: Option[Int] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owners.nonEmpty) filters = filters.filter(_.owner inSet owners)
    if (tokenSSet.nonEmpty) filters = filters.filter(_.tokenS inSet tokenSSet)
    if (tokenBSet.nonEmpty) filters = filters.filter(_.tokenB inSet tokenBSet)
    if (marketHashes.nonEmpty)
      filters = filters.filter(_.marketHash inSet marketHashes)
    if (feeTokenSet.nonEmpty)
      filters = filters.filter(_.tokenFee inSet feeTokenSet)
    if (validTime.nonEmpty)
      filters = filters
        .filter(_.validSince >= validTime.get)
        .filter(_.validUntil <= validTime.get)
    filters
  }

  def getOrders(
      statuses: Set[OrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashes: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: SortingType,
      pagingOpt: Option[CursorPaging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderFilters(
      statuses,
      owners,
      tokenSSet,
      tokenBSet,
      marketHashes,
      feeTokenSet,
      None
    )
    db.run(getPagingFilter(filters, sort, pagingOpt).result)
  }

  private def queryOrderForUserFilters(
      statuses: Set[OrderStatus],
      ownerOpt: Option[String] = None,
      tokensOpt: Option[String] = None,
      tokenbOpt: Option[String] = None,
      marketHashOpt: Option[String] = None,
      feeTokenOpt: Option[String] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (ownerOpt.nonEmpty) filters = filters.filter(_.owner === ownerOpt.get)
    if (tokensOpt.nonEmpty) filters = filters.filter(_.tokenS === tokensOpt.get)
    if (tokenbOpt.nonEmpty) filters = filters.filter(_.tokenB === tokenbOpt.get)
    if (marketHashOpt.nonEmpty)
      filters = filters.filter(_.marketHash === marketHashOpt.get)
    if (feeTokenOpt.nonEmpty)
      filters = filters.filter(_.tokenFee === feeTokenOpt.get)
    filters
  }

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      ownerOpt: Option[String] = None,
      tokensOpt: Option[String] = None,
      tokenbOpt: Option[String] = None,
      marketHashOpt: Option[String] = None,
      feeTokenOpt: Option[String] = None,
      sort: SortingType,
      pagingOpt: Option[CursorPaging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderForUserFilters(
      statuses,
      ownerOpt,
      tokensOpt,
      tokenbOpt,
      marketHashOpt,
      feeTokenOpt
    )
    db.run(getPagingFilter(filters, sort, pagingOpt).result)
  }

  //
  def getOrdersToActivate(
      activateLaggingInSecond: Int,
      limit: Int
    ): Future[Seq[RawOrder]] = {
    val availableStatus: OrderStatus =
      OrderStatus.STATUS_PENDING_ACTIVE
    val sinceTime = timeProvider
      .getTimeSeconds()
      .toInt - activateLaggingInSecond
    var filters = query
      .filter(_.status === availableStatus)
      .filter(_.validSince <= sinceTime)
      .sortBy(_.validSince.asc)
      .take(limit)
    db.run(filters.result)
  }

  def getOrdersToExpire(
      expireLeadInSeconds: Int,
      limit: Int
    ): Future[Seq[RawOrder]] = {
    val availableStatus = Seq(
      OrderStatus.STATUS_NEW,
      OrderStatus.STATUS_PENDING,
      OrderStatus.STATUS_PARTIALLY_FILLED
    )
    val untilTime = timeProvider.getTimeSeconds().toInt + expireLeadInSeconds
    var filters = query
      .filter(_.status inSet availableStatus)
      .filter(_.validUntil < untilTime)
      .sortBy(_.validUntil.asc)
      .take(limit)
    db.run(filters.result)
  }

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHashOpt: Option[String] = None,
      feeToken: Option[String] = None
    ): Future[Int] = {
    val filters = queryOrderForUserFilters(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketHashOpt,
      feeToken
    )
    db.run(filters.size.result)
  }

  private def queryOrderForRecorverFilters(
      statuses: Set[OrderStatus],
      marketEntityIds: Set[Long] = Set.empty,
      accountEntityIds: Set[Long] = Set.empty,
      paging: CursorPaging
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    if (marketEntityIds.nonEmpty && accountEntityIds.nonEmpty) {
      throw ErrorException(
        ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketEntityIds` and `accountEntityIds` could not both not empty"
      )
    }
    if (marketEntityIds.isEmpty && accountEntityIds.isEmpty) {
      throw ErrorException(
        ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketEntityIds` and `accountEntityIds` could not both empty"
      )
    }
    var filters = if (marketEntityIds.nonEmpty) {
      query.filter(_.marketEntityId inSet marketEntityIds)
    } else {
      query.filter(_.accountEntityId inSet accountEntityIds)
    }
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    filters
      .filter(_.sequenceId > paging.cursor)
      .take(paging.size)
      .sortBy(_.sequenceId.asc)
  }

  private def queryOrderForMarketAndAddress(
      statuses: Set[OrderStatus],
      marketEntityIds: Set[Long] = Set.empty,
      accountEntityIds: Set[Long] = Set.empty,
      paging: CursorPaging
    ): Future[Seq[RawOrder]] = {
    def getNextAmount(r: PositionedResult): Option[Amount] = {
      val nextBytes = r.nextBytes()
      if (null != nextBytes && nextBytes.length > 0) {
        BigInt(nextBytes)
      } else {
        None
      }
    }
    implicit val paramsResult = GetResult[RawOrder.Params](
      r =>
        RawOrder.Params(
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextInt,
          r.nextString,
          r.nextString,
          r.nextBoolean,
          r.nextString
        )
    )

    implicit val feeParamsResult = GetResult[RawOrder.FeeParams](
      r =>
        RawOrder.FeeParams(
          r.nextString,
          getNextAmount(r),
          r.nextInt,
          r.nextInt,
          r.nextInt,
          r.nextString,
          r.nextInt
        )
    )

    implicit val erc1400ParamsResult = GetResult[RawOrder.ERC1400Params](
      r =>
        RawOrder.ERC1400Params(
          TokenStandard.fromValue(r.nextInt),
          TokenStandard.fromValue(r.nextInt),
          TokenStandard.fromValue(r.nextInt),
          r.nextString,
          r.nextString,
          r.nextString
        )
    )

    implicit val stateResult = GetResult[RawOrder.State](
      r =>
        RawOrder.State(
          r.nextLong,
          r.nextLong,
          r.nextLong,
          r.nextLong,
          OrderStatus.fromValue(r.nextInt),
          getNextAmount(r),
          getNextAmount(r),
          getNextAmount(r),
          getNextAmount(r),
          getNextAmount(r),
          getNextAmount(r)
        )
    )

    implicit val totalResult = GetResult[RawOrder](
      r =>
        RawOrder(
          r.nextString,
          r.nextInt,
          r.nextString,
          r.nextString,
          r.nextString,
          getNextAmount(r),
          getNextAmount(r),
          r.nextInt,
          Some(paramsResult(r)),
          Some(feeParamsResult(r)),
          Some(erc1400ParamsResult(r)),
          Some(stateResult(r)),
          r.nextLong,
          r.nextString(),
          r.nextLong,
          r.nextLong
        )
    )
    val now = timeProvider.getTimeSeconds()
    val sql =
      sql"""
        SELECT * FROM T_ORDERS
        WHERE `status` in (#${statuses.map(_.value).mkString(",")})
        AND valid_since <= #${now}
        AND valid_until > #${now}
        AND sequence_id > #${paging.cursor}
        AND (
          market_entity_id in (#${marketEntityIds.mkString(",")})
          OR account_entity_id IN (#${accountEntityIds.mkString(",")})
        )
        ORDER BY sequence_id ASC
        LIMIT #${paging.size}
      """.as[RawOrder]
    db.run(sql).map(r => r.toSeq)
  }

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
      statuses: Set[OrderStatus],
      marketEntityIds: Set[Long] = Set.empty,
      accountEntityIds: Set[Long] = Set.empty,
      skip: CursorPaging
    ): Future[Seq[RawOrder]] = {

    if (marketEntityIds.isEmpty && accountEntityIds.isEmpty) {
      throw ErrorException(
        ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketEntityIds` and `accountEntityIds` could not both be empty"
      )
    } else {
      if (marketEntityIds.nonEmpty && accountEntityIds.nonEmpty) {
        queryOrderForMarketAndAddress(
          statuses,
          marketEntityIds,
          accountEntityIds,
          skip
        )
      } else {
        val filters = queryOrderForRecorverFilters(
          statuses,
          marketEntityIds,
          accountEntityIds,
          skip
        )
        db.run(filters.result)
      }
    }
  }

  def updateOrderStatus(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.hash === hash)
          .map(c => (c.status, c.updatedAt))
          .update(status, timeProvider.getTimeMillis)
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateOrdersStatus(
      hashes: Seq[String],
      status: OrderStatus
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.hash inSet hashes)
          .map(c => (c.status, c.updatedAt))
          .update(status, timeProvider.getTimeMillis)
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateFailed(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode] =
    for {
      _ <- Future.unit
      result <- if (!failedStatus.contains(status)) {
        Future.successful(0)
      } else {
        db.run(
          query
            .filter(_.hash === hash)
            .map(c => (c.status, c.updatedAt))
            .update(status, timeProvider.getTimeMillis)
        )
      }
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateAmounts(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.hash === hash)
          .map(
            c =>
              (
                c.actualAmountS,
                c.actualAmountB,
                c.actualAmountFee,
                c.outstandingAmountS,
                c.outstandingAmountB,
                c.outstandingAmountFee,
                c.updatedAt
              )
          )
          .update(
            state.actualAmountS,
            state.actualAmountB,
            state.actualAmountFee,
            state.outstandingAmountS,
            state.outstandingAmountB,
            state.outstandingAmountFee,
            timeProvider.getTimeMillis
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateOrderState(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.hash === hash)
          .map(
            c =>
              (
                c.actualAmountS,
                c.actualAmountB,
                c.actualAmountFee,
                c.outstandingAmountS,
                c.outstandingAmountB,
                c.outstandingAmountFee,
                c.status,
                c.updatedAt
              )
          )
          .update(
            state.actualAmountS,
            state.actualAmountB,
            state.actualAmountFee,
            state.outstandingAmountS,
            state.outstandingAmountB,
            state.outstandingAmountFee,
            state.status,
            timeProvider.getTimeMillis
          )
      )
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  private def getPagingFilter(
      filters: Query[OrderTable, OrderTable#TableElementType, Seq],
      sort: SortingType,
      pagingOpt: Option[CursorPaging] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    if (pagingOpt.nonEmpty) {
      val paging = pagingOpt.get
      val f = sort match {
        case SortingType.DESC =>
          if (paging.cursor > 0) {
            filters
              .filter(_.sequenceId < paging.cursor)
              .sortBy(_.sequenceId.desc)
          } else { // query latest
            filters.sortBy(_.sequenceId.desc)
          }
        case _ =>
          if (paging.cursor > 0) {
            filters
              .filter(_.sequenceId > paging.cursor)
              .sortBy(_.sequenceId.asc)
          } else {
            filters
              .sortBy(_.sequenceId.asc)
          }
      }
      f.take(paging.size)
    } else {
      filters
    }
  }
}
