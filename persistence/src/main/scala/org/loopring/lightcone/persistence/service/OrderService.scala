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

package org.loopring.lightcone.persistence.service

import org.loopring.lightcone.persistence.dals.OrderDal
import org.loopring.lightcone.proto._
import scala.concurrent._

trait OrderService {
  val orderDal: OrderDal

  // Save order to database, if the order already exist, return an error code.
  def saveOrder(order: RawOrder): Future[Either[RawOrder, ErrorCode]]

  // Mark the order as soft-cancelled. Returns error code if the order does not exist.
  def markOrderSoftCancelled(
      orderHashes: Seq[String]
    ): Future[Seq[UserCancelOrder.Res.Result]]
  def getOrders(hashes: Seq[String]): Future[Seq[RawOrder]]
  def getOrder(hash: String): Future[Option[RawOrder]]

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
      marketHashSet: Option[String] = None,
      feeTokenSet: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

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
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  //
  def getExpiredOrdersForMonitor(
      lastProcessTime: Int,
      processTime: Int,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]]

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeTokenSet: Option[String] = None
    ): Future[Int]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode]

  def updateAmount(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode]
}
