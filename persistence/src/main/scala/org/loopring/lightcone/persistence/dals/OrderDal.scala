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
import scala.concurrent._

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

  def saveOrder(order: XRawOrder): Future[XSaveOrderResult] = ???

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]] = ???

  def updateOrderState(
    hash: String,
    state: XRawOrder.State,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = ???

  def updateOrderStatus(
    hash: String,
    status: XOrderStatus,
    changeUpdatedAtField: Boolean = true
  ): Future[Either[XPersistenceError, String]] = ???

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
  ): Future[Seq[XRawOrder]] = ???

  def getOrdersByUpdatedAt(
    num: Int,
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    feeTokenSet: Set[String],
    updatedSince: Option[Long],
    updatedUntil: Option[Long],
    sortedByUpdatedAt: Boolean
  ): Future[Seq[XRawOrder]] = ???

  def countOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sinceId: Option[Long] = None,
    tillId: Option[Long] = None
  ): Future[Int] = ???
}
