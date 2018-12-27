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
import org.loopring.lightcone.proto.XErrorCode._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import com.mysql.jdbc.exceptions.jdbc4._
import com.typesafe.scalalogging.Logger
import scala.concurrent._
import scala.util.{Failure, Success}
import slick.lifted.Query

trait OrderDal extends BaseDalImpl[OrderTable, XRawOrder] {

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
  def saveOrder(order: XRawOrder): Future[XSaveOrderResult]

  // Returns orders with given hashes
  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]]
  // Returns orders owners with given hashes
  // Map[orderHash, XRawOrder]
  def getOrdersMap(hashes: Seq[String]): Future[Map[String, XRawOrder]]

  def getOrder(hash: String): Future[Option[XRawOrder]]

  // Get some orders. The orders should be sorted scendantly by created_at or updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrders(
      statuses: Set[XOrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]]

  def getOrdersForUser(
      statuses: Set[XOrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]]

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[XOrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None
    ): Future[Int]

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
      statuses: Set[XOrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: XSkipBySequenceId
    ): Future[Seq[XRawOrder]]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
      hash: String,
      status: XOrderStatus
    ): Future[XErrorCode]

  def updateOrdersStatus(
      hashes: Seq[String],
      status: XOrderStatus
    ): Future[XErrorCode]

  def updateFailed(
      hash: String,
      status: XOrderStatus
    ): Future[XErrorCode]

  def updateAmount(
      hash: String,
      state: XRawOrder.State
    ): Future[XErrorCode]
}

class OrderDalImpl(
  )(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext)
    extends OrderDal {
  val query = TableQuery[OrderTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  implicit val XOrderStatusColumnType = enumColumnType(XOrderStatus)
  implicit val XTokenStandardColumnType = enumColumnType(XTokenStandard)
  private[this] val logger = Logger(this.getClass)

  def saveOrder(order: XRawOrder): Future[XSaveOrderResult] = {
    db.run(
        (query += order).asTry
      )
      .map {
        case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
          XSaveOrderResult(
            error = ERR_PERSISTENCE_DUPLICATE_INSERT,
            order = None,
            alreadyExist = true
          )
        }
        case Failure(ex) ⇒ {
          logger.error(s"error : ${ex.getMessage}")
          XSaveOrderResult(
            error = ERR_PERSISTENCE_INTERNAL,
            order = None
          )
        }
        case Success(x) ⇒
          XSaveOrderResult(
            error = ERR_NONE,
            order = Some(order)
          )
      }
  }

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]] = {
    if (hashes.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      db.run(query.filter(_.hash inSet hashes).result)
    }
  }

  def getOrdersMap(hashes: Seq[String]): Future[Map[String, XRawOrder]] =
    getOrders(hashes).map(_.map(r => r.hash -> r).toMap)

  def getOrder(hash: String): Future[Option[XRawOrder]] =
    db.run(query.filter(_.hash === hash).result.headOption)

  private def queryOrderFilters(
      statuses: Set[XOrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      validTime: Option[Int] = None,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
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
      case XSort.ASC ⇒ filters.sortBy(_.sequenceId.asc)
      case XSort.DESC ⇒ filters.sortBy(_.sequenceId.desc)
      case _ ⇒ filters.sortBy(_.sequenceId.asc)
    }
    filters = skip match {
      case Some(s) ⇒ filters.drop(s.skip).take(s.take)
      case None ⇒ filters
    }
    filters
  }

  def getOrders(
      statuses: Set[XOrderStatus],
      owners: Set[String] = Set.empty,
      tokenSSet: Set[String] = Set.empty,
      tokenBSet: Set[String] = Set.empty,
      marketHashSet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]] = {
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
      statuses: Set[XOrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
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
      case XSort.ASC ⇒ filters.sortBy(_.sequenceId.asc)
      case XSort.DESC ⇒ filters.sortBy(_.sequenceId.desc)
      case _ ⇒ filters.sortBy(_.sequenceId.asc)
    }
    filters = skip match {
      case Some(s) ⇒ filters.drop(s.skip).take(s.take)
      case None ⇒ filters
    }
    filters
  }

  def getOrdersForUser(
      statuses: Set[XOrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeToken: Option[String] = None,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]] = {
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

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[XOrderStatus],
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
      statuses: Set[XOrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: XSkipBySequenceId
    ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    if (marketHashIdSet.nonEmpty && addressShardIdSet.nonEmpty) {
      throw ErrorException(
        XErrorCode.ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketHashIdSet` and `addressShardIdSet` could not both not empty"
      )
    }
    if (marketHashIdSet.isEmpty && addressShardIdSet.isEmpty) {
      throw ErrorException(
        XErrorCode.ERR_INTERNAL_UNKNOWN,
        "Invalid parameters:`marketHashIdSet` and `addressShardIdSet` could not both empty"
      )
    }
    var filters = if (marketHashIdSet.nonEmpty) {
      query.filter(_.marketHashId inSet marketHashIdSet)
    } else {
      query.filter(_.addressShardId inSet addressShardIdSet)
    }
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    filters = filters
      .filter(_.sequenceId > skip.from)
      .take(skip.take)
    filters = filters.sortBy(_.sequenceId.asc)
    filters
  }

  def queryOrderForMarketAndAddress(
      statuses: Set[XOrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: XSkipBySequenceId
    ): Future[Seq[XRawOrder]] = {
    implicit val paramsResult = GetResult[XRawOrder.Params](
      r =>
        XRawOrder.Params(
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextString,
          r.nextInt,
          r.nextString,
          r.nextString,
          r.nextBoolean,
          XTokenStandard.fromValue(r.nextInt),
          XTokenStandard.fromValue(r.nextInt),
          XTokenStandard.fromValue(r.nextInt),
          r.nextString
        )
    )
    implicit val feeParamsResult = GetResult[XRawOrder.FeeParams](
      r =>
        XRawOrder.FeeParams(
          r.nextString,
          ByteString.copyFrom(r.nextBytes()),
          r.nextInt,
          r.nextInt,
          r.nextInt,
          r.nextString,
          r.nextInt
        )
    )
    implicit val erc1400ParamsResult = GetResult[XRawOrder.ERC1400Params](
      r =>
        XRawOrder.ERC1400Params(
          r.nextString,
          r.nextString,
          r.nextString
        )
    )
    implicit val stateResult = GetResult[XRawOrder.State](
      r =>
        XRawOrder.State(
          r.nextLong,
          r.nextLong,
          r.nextLong,
          r.nextLong,
          XOrderStatus.fromValue(r.nextInt),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes()),
          ByteString.copyFrom(r.nextBytes())
        )
    )
    implicit val totalResult = GetResult[XRawOrder](
      r =>
        XRawOrder(
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
    // TODO du: 测试提交的订单 valid since为延后的10000s
    // AND valid_since <= ${now}
    // AND valid_until > ${now}
    val sql =
      sql"""
        SELECT * FROM T_ORDERS
        WHERE `status` in (${statuses.map(_.value.toString).reduceLeft(concat)})
        AND sequence_id > ${skip.from}
        AND (
          market_hash_id in (${marketHashIdSet
        .map(_.toString)
        .reduceLeft(concat)})
          OR address_shard_id IN (${addressShardIdSet
        .map(_.toString)
        .reduceLeft(concat)})
        )
        ORDER BY sequence_id ASC
        LIMIT ${skip.take}
      """.as[XRawOrder]
    db.run(sql).map(r => r.toSeq)
  }

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
      statuses: Set[XOrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: XSkipBySequenceId
    ): Future[Seq[XRawOrder]] = {
    if (marketHashIdSet.isEmpty && addressShardIdSet.isEmpty) {
      throw ErrorException(
        XErrorCode.ERR_INTERNAL_UNKNOWN,
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
      status: XOrderStatus
    ): Future[XErrorCode] =
    for {
      result ← db.run(
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
      status: XOrderStatus
    ): Future[XErrorCode] =
    for {
      result ← db.run(
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
      status: XOrderStatus
    ): Future[XErrorCode] =
    for {
      _ ← Future.unit
      failedStatus = Seq(
        XOrderStatus.STATUS_CANCELLED_BY_USER,
        XOrderStatus.STATUS_CANCELLED_LOW_BALANCE,
        XOrderStatus.STATUS_CANCELLED_LOW_FEE_BALANCE,
        XOrderStatus.STATUS_CANCELLED_TOO_MANY_ORDERS,
        XOrderStatus.STATUS_CANCELLED_TOO_MANY_FAILED_SETTLEMENTS
      )
      result ← if (!failedStatus.contains(status)) {
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
      state: XRawOrder.State
    ): Future[XErrorCode] =
    for {
      result ← db.run(
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
