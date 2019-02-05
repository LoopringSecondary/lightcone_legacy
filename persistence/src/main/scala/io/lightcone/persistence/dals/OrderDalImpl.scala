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
import com.google.protobuf.ByteString
import com.mysql.jdbc.exceptions.jdbc4._
import com.typesafe.scalalogging.Logger
import io.lightcone.lib._
import io.lightcone.persistence.base._
import io.lightcone.proto._
import io.lightcone.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
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

  def saveOrder(order: RawOrder): Future[PersistOrder.Res] = {
    db.run((query += order).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) => {
        PersistOrder.Res(
          error = ERR_PERSISTENCE_DUPLICATE_INSERT,
          order = None,
          alreadyExist = true
        )
      }
      case Failure(ex) => {
        logger.error(s"error : ${ex.getMessage}")
        PersistOrder.Res(error = ERR_PERSISTENCE_INTERNAL, order = None)
      }
      case Success(x) =>
        PersistOrder.Res(error = ERR_NONE, order = Some(order))
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
      marketIds: Set[Long] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      validTime: Option[Int] = None,
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owners.nonEmpty) filters = filters.filter(_.owner inSet owners)
    if (tokenSSet.nonEmpty) filters = filters.filter(_.tokenS inSet tokenSSet)
    if (tokenBSet.nonEmpty) filters = filters.filter(_.tokenB inSet tokenBSet)
    if (marketIds.nonEmpty)
      filters = filters.filter(_.marketId inSet marketIds)
    if (feeTokenSet.nonEmpty)
      filters = filters.filter(_.tokenFee inSet feeTokenSet)
    if (validTime.nonEmpty)
      filters = filters
        .filter(_.validSince >= validTime.get)
        .filter(_.validUntil <= validTime.get)
    if (sort.nonEmpty) filters = sort.get match {
      case SortingType.ASC  => filters.sortBy(_.sequenceId.asc)
      case SortingType.DESC => filters.sortBy(_.sequenceId.desc)
      case _                => filters.sortBy(_.sequenceId.asc)
    }
    filters = pagingOpt match {
      case Some(paging) => filters.drop(paging.skip).take(paging.size)
      case None         => filters
    }
    filters
  }

  def getOrders(
      statuses: Set[OrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketIds: Set[Long] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderFilters(
      statuses,
      owners,
      tokenSSet,
      tokenBSet,
      marketIds,
      feeTokenSet,
      None,
      sort,
      skip
    )
    db.run(filters.result)
  }

  private def queryOrderForUserFilters(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketId: Option[Long] = None,
      feeToken: Option[String] = None,
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owner.nonEmpty) filters = filters.filter(_.owner === owner)
    if (tokenS.nonEmpty) filters = filters.filter(_.tokenS === tokenS)
    if (tokenB.nonEmpty) filters = filters.filter(_.tokenB === tokenB)
    if (marketId.nonEmpty)
      filters = filters.filter(_.marketId === marketId)
    if (feeToken.nonEmpty) filters = filters.filter(_.tokenFee === feeToken)
    if (sort.nonEmpty) filters = sort.get match {
      case SortingType.ASC  => filters.sortBy(_.sequenceId.asc)
      case SortingType.DESC => filters.sortBy(_.sequenceId.desc)
      case _                => filters.sortBy(_.sequenceId.asc)
    }
    filters = pagingOpt match {
      case Some(paging) => filters.drop(paging.skip).take(paging.size)
      case None         => filters
    }
    filters
  }

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketId: Option[Long] = None,
      feeToken: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderForUserFilters(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketId,
      feeToken,
      sort,
      skip
    )
    db.run(filters.result)
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
      .toInt + activateLaggingInSecond
    var filters = query
      .filter(_.status === availableStatus)
      .filter(_.validSince >= sinceTime)
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
      marketId: Option[Long] = None,
      feeToken: Option[String] = None
    ): Future[Int] = {
    val filters = queryOrderForUserFilters(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketId,
      feeToken,
      None,
      None
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
          ByteString.copyFrom(r.nextBytes()),
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
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes())
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
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          r.nextInt,
          Some(paramsResult(r)),
          Some(feeParamsResult(r)),
          Some(erc1400ParamsResult(r)),
          Some(stateResult(r)),
          r.nextLong,
          r.nextLong,
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

  def getCutoffAffectedOrders(
      retrieveCondition: RetrieveOrdersToCancel,
      take: Int
    ): Future[Seq[RawOrder]] = {
    //TODO du：暂时不考虑broker，owner必传
    if (retrieveCondition.owner.isEmpty) {
      throw ErrorException(
        ERR_INTERNAL_UNKNOWN,
        "owner in CutoffEvent is empty"
      )
    }
    var filters = query
      .filter(_.owner === retrieveCondition.owner)
      .filter(_.status inSet activeStatus)
      .filter(_.validSince <= retrieveCondition.cutoff.toInt)
    if (retrieveCondition.marketHash.nonEmpty) {
      val marketId = MarketHash.hashToId(retrieveCondition.marketHash)
      filters = filters.filter(_.marketId === marketId)
    }
    filters = filters
      .sortBy(_.sequenceId.asc)
      .take(take)
    db.run(filters.result)
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
}
