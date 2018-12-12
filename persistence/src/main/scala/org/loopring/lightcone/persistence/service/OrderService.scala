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

import org.loopring.lightcone.proto._
import scala.concurrent._

trait OrderService {
  def saveOrder(order: XRawOrder): Future[Either[XRawOrder, XErrorCode]]
  def markOrderSoftCancelled(orderHashes: Seq[String]): Future[Seq[Either[XErrorCode, String]]]

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]]
  def getOrder(hash: String): Future[Option[XRawOrder]]

  def getOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    marketHashSet: Set[Long] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sort: Option[XSort] = None,
    skip: Option[XSkip] = None
  ): Future[Seq[XRawOrder]]

  def getOrdersForUser(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    marketHashSet: Set[Long] = Set.empty,
    feeTokenSet: Set[String] = Set.empty,
    sort: Option[XSort] = None,
    skip: Option[XSkip] = None
  ): Future[Seq[XRawOrder]]

  // Get some orders between updatedSince and updatedUntil. The orders are sorted by updated_at
  // indicatd by the sortedByUpdatedAt param.
  def getOrdersForRecover(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    marketHashSet: Set[Long] = Set.empty,
    validTime: Option[Int] = None,
    sort: Option[XSort] = None,
    skip: Option[XSkip] = None
  ): Future[Seq[XRawOrder]]

  // Count the number of orders
  def countOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String] = Set.empty,
    tokenSSet: Set[String] = Set.empty,
    tokenBSet: Set[String] = Set.empty,
    marketHashSet: Set[Long] = Set.empty,
    feeTokenSet: Set[String] = Set.empty
  ): Future[Int]

  // Update order's status and update the updated_at timestamp if changeUpdatedAtField is true.
  // Returns Left(error) if this operation fails, or Right(string) the order's hash.
  def updateOrderStatus(
    hash: String,
    status: XOrderStatus
  ): Future[Either[XErrorCode, String]]

  def updateAmount(
    hash: String,
    state: XRawOrder.State
  ): Future[Either[XErrorCode, String]]
}
