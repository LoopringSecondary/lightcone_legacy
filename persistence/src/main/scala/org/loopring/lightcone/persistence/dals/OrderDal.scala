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
import org.loopring.lightcone.proto.persistence._
import org.loopring.lightcone.proto.core._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.JdbcProfile
import slick.basic._
import slick.lifted.{CanBeQueryCondition, Query}
import com.mysql.jdbc.exceptions.jdbc4._

import scala.concurrent._
import scala.util.{Failure, Success}

trait OrderDal
  extends UniqueHashDalImpl[OrderTable, XRawOrder] {

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
  def getOrder(hash: String): Future[Option[XRawOrder]] = getOrders(Seq(hash)).map(_.headOption)

  // Save order's state information, this should not change orther's other values.
  // Note that the update_at field should be updated to the current timestamp if
  // changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderState(
    hash: String,
    state: XRawOrder.State,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
    hash: String,
    status: XOrderStatus,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]]

  // Get some orders. The orders should be sorted scendantly by created_at or updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrders(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None,
    sortedByUpdatedAt: Boolean = true
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
}

class OrderDalImpl()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    val ec: ExecutionContext
) extends OrderDal {
  val query = TableQuery[OrderTable]
  def getRowHash(row: XRawOrder) = row.hash

  def saveOrder(order: XRawOrder): Future[XSaveOrderResult] = {
    if (order.hash.isEmpty || order.version <= 0 || order.owner.isEmpty || order.tokenS.isEmpty || order.tokenB.isEmpty ||
      order.amountS.isEmpty || order.amountB.isEmpty || order.validSince <= 0 || order.id > 0 || order.state.nonEmpty) {
      Future.successful(XSaveOrderResult(error = XPersistenceError.PERS_INVALID_PARAMTERS, order = Some(order)))
    } else {
      val now = System.currentTimeMillis()
      val state = XRawOrder.State(createdAt = now, updatedAt = now, status = XOrderStatus.STATUS_NEW)
      db.run((query returning query.map(_.id) ++= Seq(order.copy(state = Some(state)))).asTry).map {
        case Failure(e: MySQLIntegrityConstraintViolationException) ⇒ {
          XSaveOrderResult(error = XPersistenceError.PERS_ERROR_DUPLICATE_INSERT, order = Some(order), alreadyExist = true)
        }
        case Failure(ex) ⇒ {
          // TODO du: print some log
          // log(s"error : ${ex.getMessage}")
          XSaveOrderResult(error = XPersistenceError.PERS_ERROR_INTERNAL, order = Some(order))
        }
        case Success(x) ⇒ XSaveOrderResult(error = XPersistenceError.PERS_ERR_NONE, order = Some(order.copy(id = x.head)))
      }
    }
  }

  def getOrderByHash(hash: String): Future[Option[XRawOrder]] = {
    val queries = query
      .filter(_.hash === hash)
    db.run(queries.result.headOption)
  }

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]] = {
    if (hashes.isEmpty) {
      Future.successful(Seq.empty)
    } else {
      val queries = query
        .filter(_.hash inSet hashes)
      db.run(queries.result)
    }
  }

  def updateOrderState(
    hash: String,
    state: XRawOrder.State,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = for {
    _ ← Future.unit
    // TODO du: 1. 排除updateAt字段的更新未解决 2.是否应该传入updateToState(作为update的值), fromState(作为where条件) 3.公用的timeProvider
    stateProjection = if (changeUpdatedAtField) state.copy(updatedAt = System.currentTimeMillis()) else state.copy(updatedAt = 0l)
    result ← db.run(query
      .filter(_.hash === hash)
      .map(c ⇒ c.stateProjection)
      .update(Some(stateProjection)))
  } yield {
    if (result >= 1) Right(hash)
    else Left(XPersistenceError.PERS_ERROR_UPDATE_FAILED)
  }

  def updateOrderStatus(
    hash: String,
    status: XOrderStatus,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = for {
    _ ← Future.unit
    stateProjection = if (changeUpdatedAtField) XRawOrder.State().copy(updatedAt = System.currentTimeMillis(), status = status)
    //TODO du: 和上面同样的问题，待解决只更新一个字段
    else XRawOrder.State().copy(status = status)
    result ← db.run(query
      .map(_.stateProjection)
      .update(Some(stateProjection)))
  } yield {
    if (result >= 1) Right(hash)
    else Left(XPersistenceError.PERS_ERROR_UPDATE_FAILED)
  }

  def queryOrderFilters(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None
  ): Query[OrderTable, OrderTable#TableElementType, Seq] = {
    var filters = query.filter(_.id > 0l)
    // TODO du: 待解决的filter查询条件
//    implicit val XOrderStatusCanBeQueryCondition : CanBeQueryCondition[XOrderStatus] =
//      new CanBeQueryCondition[XOrderStatus] {
//        def apply(value: XOrderStatus) = new LiteralColumn(value)
//      }
//     if (statuses.nonEmpty) filters = filters.filter(_.status inSet statuses)
    if (owners.nonEmpty) filters = filters.filter(_.owner inSet owners)
    if (tokenSSet.nonEmpty) filters = filters.filter(_.tokenS inSet tokenSSet)
    if (tokenBSet.nonEmpty) filters = filters.filter(_.tokenB inSet tokenBSet)
    if (feeTokenSet.nonEmpty) filters = filters.filter(_.feeToken inSet feeTokenSet)
    if (sinceId.nonEmpty) filters = filters.filter(_.validSince >= sinceId.get.toInt)
    if (tillId.nonEmpty) filters = filters.filter(_.validUntil >= tillId.get.toInt)
    filters
  }

  def getOrders(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None,
    sortedByUpdatedAt: Boolean = true
  ): Future[Seq[XRawOrder]] = {
    if (num <= 0 || (statuses.isEmpty && owners.isEmpty && tokenSSet.isEmpty && tokenBSet.isEmpty
      && feeTokenSet.isEmpty && sinceId.isEmpty && tillId.isEmpty)) {
      Future.successful(Seq.empty)
    } else {
      val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId)
      if (sortedByUpdatedAt) {
        db.run(filters
          .sortBy(_.updatedAt.desc)
          .take(num)
          .result)
      } else {
        db.run(filters
          .sortBy(_.createdAt.desc)
          .take(num)
          .result)
      }
    }
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
    if (statuses.isEmpty && owners.isEmpty && tokenSSet.isEmpty && tokenBSet.isEmpty && feeTokenSet.isEmpty
      && sinceId.isEmpty && tillId.isEmpty) {
      Future.successful(0)
    } else {
      val filters = queryOrderFilters(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId)
      db.run(filters
        .sortBy(_.updatedAt.desc)
        .size
        .result)
    }
  }
}
