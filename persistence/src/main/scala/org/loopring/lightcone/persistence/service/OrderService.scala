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

import com.google.inject.Inject
import com.google.inject.name.Named
import org.loopring.lightcone.persistence.dals.{ OrderDal, OrderDalImpl }
import org.loopring.lightcone.proto.actors.XSaveOrderResult
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

trait OrderService {

  val orderDal: OrderDal

  def submitOrder(order: XRawOrder): Future[XSaveOrderResult]

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
  ): Future[Either[XPersistenceError, String]]

  def updateAmount(
    hash: String,
    state: XRawOrder.State
  ): Future[Either[XPersistenceError, String]]
}

class OrderServiceImpl @Inject() (
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext
) extends OrderService {
  val orderDal: OrderDal = new OrderDalImpl()

  def submitOrder(order: XRawOrder): Future[XSaveOrderResult] = {
    //TODO du：验证订单有效，更新状态
    orderDal.saveOrder(order)
  }

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]] = orderDal.getOrders(hashes)

  def getOrder(hash: String): Future[Option[XRawOrder]] = orderDal.getOrder(hash)

  def getOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    marketHashSet: Set[Long],
    feeTokenSet: Set[String],
    sort: Option[XSort],
    skip: Option[XSkip]
  ): Future[Seq[XRawOrder]] = orderDal.getOrders(statuses, owners, tokenSSet, tokenBSet, marketHashSet, feeTokenSet,
    sort, skip)

  def getOrdersForUser(
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    marketHashSet: Set[Long],
    feeTokenSet: Set[String],
    sort: Option[XSort],
    skip: Option[XSkip]
  ): Future[Seq[XRawOrder]] = orderDal.getOrdersForUser(statuses, owners, tokenSSet, tokenBSet, marketHashSet,
    feeTokenSet, sort, skip)

  def getOrdersForRecover(
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    marketHashSet: Set[Long],
    validTime: Option[Int],
    sort: Option[XSort],
    skip: Option[XSkip]
  ): Future[Seq[XRawOrder]] = orderDal.getOrdersForRecover(statuses, owners, tokenSSet, tokenBSet, marketHashSet,
    validTime, sort, skip)

  def countOrders(
    statuses: Set[XOrderStatus],
    owners: Set[String],
    tokenSSet: Set[String],
    tokenBSet: Set[String],
    marketHashSet: Set[Long],
    feeTokenSet: Set[String]
  ): Future[Int] = orderDal.countOrders(statuses, owners, tokenSSet, tokenBSet, marketHashSet, feeTokenSet)

  def updateOrderStatus(
    hash: String,
    status: XOrderStatus
  ): Future[Either[XPersistenceError, String]] = {
    // TODO du: 验证订单状态 从[new, partially] -> pending， 从cancel不能更新其他
    orderDal.updateOrderStatus(hash, status)
  }

  def updateAmount(
    hash: String,
    state: XRawOrder.State
  ): Future[Either[XPersistenceError, String]] = orderDal.updateAmount(hash, state)
}
