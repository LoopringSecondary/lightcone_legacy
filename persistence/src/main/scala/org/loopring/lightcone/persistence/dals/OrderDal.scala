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

import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.persistence._
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import com.mysql.jdbc.exceptions.jdbc4._
import scala.concurrent._
import scala.util.{ Failure, Success }
import org.loopring.lightcone.persistence.utils._
import org.loopring.lightcone.proto.core.XRawOrder._
import slick.lifted.Query

trait OrderDal
  extends BaseDalImpl[OrderTable, XRawOrder] {

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
  def getOrder(hash: String): Future[Option[XRawOrder]]

  // Get some orders. The orders should be sorted scendantly by created_at or updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrders(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]]

  def getOrdersBySequence(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None,
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]]

  // Get some orders between updatedSince and updatedUntil. The orders are sorted by updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrdersByUpdatedAt(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    updatedSince: Option[Long] = None,
    updatedUntil: Option[Long] = None,
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]]

  // Count the number of orders
  def countOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None
  ): Future[Int]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
    hash: String,
    status: XOrderStatus,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]]

  def updateFailed(
    hash: String,
    status: XOrderStatus
  ): Future[Either[XPersistenceError, String]]

  def updateAmount(
    hash: String,
    state: XRawOrder.State,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]]
}

class OrderDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends OrderDal {
  val query = TableQuery[OrderTable]
  def getRowHash(row: XRawOrder) = row.hash
  val timeProvider = new SystemTimeProvider()
  implicit val XOrderStatusCxolumnType = enumColumnType(XOrderStatus)
  implicit val XTokenStandardCxolumnType = enumColumnType(XTokenStandard)

  def saveOrder(order: XRawOrder): Future[XSaveOrderResult] = {
    val now = timeProvider.getTimeMillis
    val state = XRawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = XOrderStatus.STATUS_NEW
    )
    val a = (for {
      raw ← query += order.copy(state = Some(state))
    } yield raw).transactionally
    db.run(a.asTry).map {
      case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
        XSaveOrderResult(
          error = XPersistenceError.PERS_ERR_DUPLICATE_INSERT,
          order = None,
          alreadyExist = true
        )
      }
      case Failure(ex) ⇒ {
        // TODO du: print some log
        // log(s"error : ${ex.getMessage}")
        XSaveOrderResult(
          error = XPersistenceError.PERS_ERR_INTERNAL,
          order = None
        )
      }
      case Success(x) ⇒ XSaveOrderResult(
        error = XPersistenceError.PERS_ERR_NONE,
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

  def getOrder(hash: String): Future[Option[XRawOrder]] = db.run(query.filter(_.hash === hash).result.headOption)

  private def queryOrderFilters(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None,
    sortBy: Option[XOrderSortBy] = None
  ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.sequenceId > 0l)
    if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owners.nonEmpty) filters = filters.filter(_.owner inSet owners)
    if (tokenSSet.nonEmpty) filters = filters.filter(_.tokenS inSet tokenSSet)
    if (tokenBSet.nonEmpty) filters = filters.filter(_.tokenB inSet tokenBSet)
    if (feeTokenSet.nonEmpty) filters = filters.filter(_.tokenFee inSet feeTokenSet)
    if (sinceId.nonEmpty) filters = filters.filter(_.sequenceId >= sinceId.get)
    if (tillId.nonEmpty) filters = filters.filter(_.sequenceId <= tillId.get)
    filters = sortBy match {
      case Some(sort) ⇒ sort.sort match {
        case XOrderSortBy.Sort.CreatedAt(value) ⇒ filters.sortBy(_.createdAt.desc)
        case XOrderSortBy.Sort.UpdatedAt(value) ⇒ filters.sortBy(_.updatedAt.desc)
        case _ ⇒ filters
      }
      case None ⇒ filters
    }
    filters
  }

  def getOrders(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]] = {
    val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, None, None, sortedBy)
    db.run(filters
      .take(num)
      .result)
  }

  def getOrdersBySequence(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None,
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]] = {
    val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, sortedBy)
    db.run(filters
      .result)
  }

  def getOrdersByUpdatedAt(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    feeTokenSet: Set[String],
    updatedSince: Option[Long],
    updatedUntil: Option[Long],
    sortedBy: Option[XOrderSortBy] = None
  ): Future[Seq[XRawOrder]] = {
    val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, None, None, sortedBy)
    db.run(filters
      .filter(_.updatedAt >= updatedSince.get)
      .filter(_.updatedAt <= updatedUntil.get)
      .take(num)
      .result)
  }

  def countOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None
  ): Future[Int] = {
    val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, None)
    db.run(filters
      .size
      .result)
  }

  def updateOrderStatus(
    hash: String,
    status: XOrderStatus,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = for {
    result ← if (changeUpdatedAtField) {
      db.run(query
        .filter(_.hash === hash)
        .map(c ⇒ (c.status, c.updatedAt))
        .update(status, timeProvider.getTimeMillis))
    } else {
      db.run(query
        .filter(_.hash === hash)
        .map(_.status)
        .update(status))
    }
  } yield {
    if (result >= 1) Right(hash)
    else Left(XPersistenceError.PERS_ERR_UPDATE_FAILED)
  }

  def updateFailed(
    hash: String,
    status: XOrderStatus
  ): Future[Either[XPersistenceError, String]] = for {
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
      db.run(query
        .filter(_.hash === hash)
        .map(c ⇒ (c.status, c.updatedAt))
        .update(status, timeProvider.getTimeMillis))
    }
  } yield {
    if (result >= 1) Right(hash)
    else Left(XPersistenceError.PERS_ERR_UPDATE_FAILED)
  }

  def updateAmount(
    hash: String,
    state: XRawOrder.State,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = for {
    result ← if (changeUpdatedAtField) {
      db.run(query
        .filter(_.hash === hash)
        .map(c ⇒ (
          c.actualAmountS,
          c.actualAmountB,
          c.actualAmountFee,
          c.outstandingAmountS,
          c.outstandingAmountB,
          c.outstandingAmountFee,
          c.updatedAt
        ))
        .update(
          state.actualAmountS,
          state.actualAmountB,
          state.actualAmountFee,
          state.outstandingAmountS,
          state.outstandingAmountB,
          state.outstandingAmountFee,
          timeProvider.getTimeMillis
        ))
    } else {
      db.run(query
        .filter(_.hash === hash)
        .map(c ⇒ (
          c.actualAmountS,
          c.actualAmountB,
          c.actualAmountFee,
          c.outstandingAmountS,
          c.outstandingAmountB,
          c.outstandingAmountFee
        ))
        .update(
          state.actualAmountS,
          state.actualAmountB,
          state.actualAmountFee,
          state.outstandingAmountS,
          state.outstandingAmountB,
          state.outstandingAmountFee
        ))
    }
  } yield {
    if (result >= 1) Right(hash)
    else Left(XPersistenceError.PERS_ERR_UPDATE_FAILED)
  }
}
