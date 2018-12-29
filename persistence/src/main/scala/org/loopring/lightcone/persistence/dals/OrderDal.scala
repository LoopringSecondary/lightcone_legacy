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

import com.google.protobuf.ByteString
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import com.mysql.jdbc.exceptions.jdbc4._
import com.typesafe.scalalogging.Logger
import scala.concurrent._
import scala.util.{Failure, Success}
import slick.lifted.Query

trait OrderDal extends BaseDalImpl[OrderTable, RawOrder] {

  // Save a order to the database and returns the saved order and indicate
  // whether the order was perviously saved or not.
  // When a order is saved, make sure the following fields are NON-empty:
  // - string hash
  // - int32  version
  // - string owner
  // - string token_s
  // - string token_b
  // - bytes  amount_s
  // - bytes  amount_b
  // - int32  valid_since
  //
  // and the following fields are EMPTY:
  // - int64  id
  // - State state
  //
  // and the following files are kept as-is:
  // - Params params
  // - FeeParams fee_params
  // - ERC1400Params erc1400_params
  // also, if the order is NEW, the status field needs to save as NEW
  // and the created_at and updated_at fileds should both be the current timestamp;
  // if the order already exists, no field should be changed.
  def saveOrder(order: RawOrder): Future[PersistOrder.Res]

  // Returns orders with given hashes
  def getOrders(hashes: Seq[String]): Future[Seq[RawOrder]]
  // Returns orders owners with given hashes
  // Map[orderHash, RawOrder]
  def getOrdersMap(hashes: Seq[String]): Future[Map[String, RawOrder]]

  def getOrder(hash: String): Future[Option[RawOrder]]

  // Get some orders. The orders should be sorted scendantly by created_at or updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrders(
      statuses: Set[OrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None
    ): Future[Int]

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
                           statuses: Set[OrderStatus],
                           marketHashIdSet: Set[Int] = Set.empty,
                           addressShardIdSet: Set[Int] = Set.empty,
                           skip: CursorPaging
                         ): Future[Seq[RawOrder]]
  //
  def getEffectiveOrdersForMonitor(
      lastProcessTime: Int,
      processTime: Int,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]]

  //
  def getExpiredOrdersForMonitor(
      lastProcessTime: Int,
      processTime: Int,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]]

  // Count the number of orders
  def countOrdersForRecover(
      statuses: Set[XOrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty
    ): Future[Int]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode]

  def updateOrdersStatus(
      hashes: Seq[String],
      status: OrderStatus
    ): Future[ErrorCode]

  def updateFailed(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode]

  def updateAmount(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode]
}

class OrderDalImpl(
  )(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends OrderDal {
  val query = TableQuery[OrderTable]
  def getRowHash(row: RawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  implicit val OrderStatusColumnType = enumColumnType(OrderStatus)
  implicit val TokenStandardColumnType = enumColumnType(TokenStandard)
  private[this] val logger = Logger(this.getClass)

  def saveOrder(order: RawOrder): Future[PersistOrder.Res] = {
    db.run((query += order).asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
        PersistOrder.Res(
          error = ERR_PERSISTENCE_DUPLICATE_INSERT,
          order = None,
          alreadyExist = true
        )
      }
      case Failure(ex) ⇒ {
        logger.error(s"error : ${ex.getMessage}")
        PersistOrder.Res(error = ERR_PERSISTENCE_INTERNAL, order = None)
      }
      case Success(x) ⇒
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
      marketHashSet: Set[String] = Set.empty,
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
    if (marketHashSet.nonEmpty)
      filters = filters.filter(_.marketHash inSet marketHashSet)
    if (feeTokenSet.nonEmpty)
      filters = filters.filter(_.tokenFee inSet feeTokenSet)
    if (validTime.nonEmpty)
      filters = filters
        .filter(_.validSince >= validTime.get)
        .filter(_.validUntil <= validTime.get)
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

  def getOrders(
      statuses: Set[OrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderFilters(
      statuses,
      owners,
      tokenSSet,
      tokenBSet,
      marketHashSet,
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
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[SortingType] = None,
      pagingOpt: Option[Paging] = None
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0L)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owner.nonEmpty) filters = filters.filter(_.owner === owner)
    if (tokenS.nonEmpty) filters = filters.filter(_.tokenS === tokenS)
    if (tokenB.nonEmpty) filters = filters.filter(_.tokenB === tokenB)
    if (marketHash.nonEmpty)
      filters = filters.filter(_.marketHash === marketHash)
    if (feeToken.nonEmpty) filters = filters.filter(_.tokenFee === feeToken)
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

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]] = {
    val filters = queryOrderForUserFilters(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketHash,
      feeToken,
      sort,
      skip
    )
    db.run(filters.result)
  }

  //
  def getEffectiveOrdersForMonitor(
      lastProcessTime: Int,
      processTime: Int,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]] = {
    val availableStatus =
      Seq(XOrderStatus.STATUS_NEW, XOrderStatus.STATUS_PENDING)
    var filters = query
      .filter(_.status inSet availableStatus)
      .filter(_.validSince > lastProcessTime)
      .filter(_.validSince < processTime)
      .sortBy(_.sequenceId.asc)
//        .filter(r => r.validSince > r.createdAt ) //todo:
    filters = skip match {
      case Some(s) ⇒ filters.drop(s.skip).take(s.take)
      case None ⇒ filters
    }
    db.run(filters.result)
  }

  //
  def getExpiredOrdersForMonitor(
      lastProcessTime: Int,
      processTime: Int,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]] = {
    val availableStatus = Seq(
      XOrderStatus.STATUS_NEW,
      XOrderStatus.STATUS_PENDING,
      XOrderStatus.STATUS_PARTIALLY_FILLED
    )
    var filters = query
      .filter(_.status inSet availableStatus)
      .filter(_.validUntil > lastProcessTime)
      .filter(_.validUntil < processTime) //todo:需要确认下
      .sortBy(_.sequenceId.asc)
    //        .filter(r => r.validSince > r.createdAt )
    filters = skip match {
      case Some(s) ⇒ filters.drop(s.skip).take(s.take)
      case None ⇒ filters
    }
    db.run(filters.result)
  }

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None
    ): Future[Int] = {
    val filters = queryOrderForUserFilters(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketHash,
      feeToken,
      None,
      None
    )
    db.run(filters.size.result)
  }

  private def queryOrderForRecorverFilters(
      statuses: Set[OrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      paging: CursorPaging
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    if (marketHashIdSet.nonEmpty && addressShardIdSet.nonEmpty) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketHashIdSet` and `addressShardIdSet` could not both not empty"
      )
    }
    if (marketHashIdSet.isEmpty && addressShardIdSet.isEmpty) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketHashIdSet` and `addressShardIdSet` could not both empty"
      )
    }
    var filters = if (marketHashIdSet.nonEmpty) {
      query.filter(_.marketHashId inSet marketHashIdSet)
    } else {
      query.filter(_.addressShardId inSet addressShardIdSet)
    }
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    filters
      .filter(_.sequenceId > paging.cursor)
      .take(paging.size)
      .sortBy(_.sequenceId.asc)
  }

  private def queryOrderForMarketAndAddress(
      statuses: Set[OrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
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
          TokenStandard.fromValue(r.nextInt),
          TokenStandard.fromValue(r.nextInt),
          TokenStandard.fromValue(r.nextInt),
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
      r => RawOrder.ERC1400Params(r.nextString, r.nextString, r.nextString)
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
          r.nextString,
          r.nextInt,
          r.nextInt
        )
    )
    val concat: (String, String) => String = (left, right) => {
      left + ", " + right
    }
    val now = timeProvider.getTimeSeconds()
    val sql =
      sql"""
        SELECT * FROM T_ORDERS
        WHERE `status` in (${statuses.map(_.value).mkString(",")})
        AND valid_since <= ${now}
        AND valid_until > ${now}
        AND sequence_id > ${paging.cursor}
        AND (
          market_hash_id in (${marketHashIdSet.mkString(",")})
          OR address_shard_id IN (${addressShardIdSet.mkString(",")})
        )
        ORDER BY sequence_id ASC
        LIMIT ${paging.size}
      """.as[RawOrder]
    db.run(sql).map(r => r.toSeq)
  }

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
      statuses: Set[OrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: CursorPaging
    ): Future[Seq[RawOrder]] = {
    if (marketHashIdSet.isEmpty && addressShardIdSet.isEmpty) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketHashIdSet` and `addressShardIdSet` could not both empty"
      )
    } else {
      if (marketHashIdSet.nonEmpty && addressShardIdSet.nonEmpty) {
        queryOrderForMarketAndAddress(
          statuses,
          marketHashIdSet,
          addressShardIdSet,
          skip
        )
      } else {
        val filters = queryOrderForRecorverFilters(
          statuses,
          marketHashIdSet,
          addressShardIdSet,
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
          .map(c ⇒ (c.status, c.updatedAt))
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
          .map(c ⇒ (c.status, c.updatedAt))
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
      failedStatus = Seq(
        OrderStatus.STATUS_CANCELLED_BY_USER,
        OrderStatus.STATUS_CANCELLED_LOW_BALANCE,
        OrderStatus.STATUS_CANCELLED_LOW_FEE_BALANCE,
        OrderStatus.STATUS_CANCELLED_TOO_MANY_ORDERS,
        OrderStatus.STATUS_CANCELLED_TOO_MANY_FAILED_SETTLEMENTS
      )
      result <- if (!failedStatus.contains(status)) {
        Future.successful(0)
      } else {
        db.run(
          query
            .filter(_.hash === hash)
            .map(c ⇒ (c.status, c.updatedAt))
            .update(status, timeProvider.getTimeMillis)
        )
      }
    } yield {
      if (result >= 1) ERR_NONE
      else ERR_PERSISTENCE_UPDATE_FAILED
    }

  def updateAmount(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] =
    for {
      result <- db.run(
        query
          .filter(_.hash === hash)
          .map(
            c ⇒
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
}
