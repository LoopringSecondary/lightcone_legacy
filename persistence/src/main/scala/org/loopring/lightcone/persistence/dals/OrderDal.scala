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
import com.google.protobuf.ByteString
import com.mysql.jdbc.exceptions.jdbc4._
import com.typesafe.scalalogging.Logger
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.base._
import org.loopring.lightcone.persistence.tables._
import org.loopring.lightcone.proto._
import org.loopring.lightcone.proto.ErrorCode._
import slick.jdbc.MySQLProfile.api._
import slick.jdbc.{GetResult, JdbcProfile}
import slick.basic._
import slick.lifted.Query
import scala.concurrent._
import scala.util.{Failure, Success}

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
      marketKeySet: Set[String] = Set.empty,
      feeTokenSet: Set[String] = Set.empty,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketKey: Option[String] = None,
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
      marketKey: Option[String] = None,
      feeToken: Option[String] = None
    ): Future[Int]

  // Get some orders larger than given sequenceId. The orders are ascending sorted by sequenceId
  def getOrdersForRecover(
      statuses: Set[OrderStatus],
      marketKeyIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: CursorPaging
    ): Future[Seq[RawOrder]]

  def getCutoffAffectedOrders(
      retrieveCondition: RetrieveOrdersToCancel,
      take: Int
    ): Future[Seq[RawOrder]]

  def getOrdersToActivate(
      latestProcessTime: Int,
      processTime: Int,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  def getOrdersToExpire(
      latestProcessTime: Int,
      processTime: Int,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

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
