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

package io.lightcone.persistence

import com.google.inject.Inject
import com.google.inject.name.Named
import io.lightcone.lib._
import io.lightcone.persistence.dals._
import io.lightcone.core._
import io.lightcone.relayer.data._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

class OrderServiceImpl @Inject()(
    implicit
    val ec: ExecutionContext,
    orderDal: OrderDal)
    extends OrderService {

  import ErrorCode._

  private def giveUserOrder(order: Option[RawOrder]): Option[RawOrder] = {
    order match {
      case Some(o) =>
        val state = o.state.get
        val returnState =
          RawOrder.State(status = state.status, createdAt = state.createdAt)
        Some(
          o.copy(
            state = Some(returnState),
            sequenceId = 0,
            marketId = 0,
            marketEntityId = 0,
            accountEntityId = 0
          )
        )
      case None => None
    }
  }

  // Save order to database, if the order already exist, return an error code.
  def saveOrder(order: RawOrder): Future[Either[RawOrder, ErrorCode]] = {
    if (order.accountEntityId < 0 || order.marketEntityId < 0) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"Invalid accountEntityId:[${order.accountEntityId}] or " +
          s"marketEntityId:[${order.marketEntityId}]"
      )
    }
    orderDal.saveOrder(order).map { r =>
      if (r.error == ErrorCode.ERR_NONE) {
        Left(r.order.get)
      } else {
        Right(r.error)
      }
    }
  }

  def getOrders(hashes: Seq[String]): Future[Seq[RawOrder]] =
    orderDal.getOrders(hashes)

  def getOrder(hash: String): Future[Option[RawOrder]] =
    orderDal.getOrder(hash)

  def getOrders(
      statuses: Set[OrderStatus],
      owners: Set[String],
      tokenSSet: Set[String],
      tokenBSet: Set[String],
      marketIds: Set[Long],
      feeTokenSet: Set[String],
      sort: Option[SortingType],
      skip: Option[Paging]
    ): Future[Seq[RawOrder]] =
    orderDal
      .getOrders(
        statuses,
        owners,
        tokenSSet,
        tokenBSet,
        marketIds,
        feeTokenSet,
        sort,
        skip
      )
      .map(_.map(r => giveUserOrder(Some(r)).get))

  def getOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketIds: Option[Long] = None,
      feeTokenSet: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[Paging] = None
    ): Future[Seq[RawOrder]] =
    orderDal
      .getOrdersForUser(
        statuses,
        owner,
        tokenS,
        tokenB,
        marketIds,
        feeTokenSet,
        sort,
        skip
      )
      .map(_.map(r => giveUserOrder(Some(r)).get))

  def getOrdersForRecover(
      statuses: Set[OrderStatus],
      marketEntityIds: Set[Long] = Set.empty,
      accountEntityIds: Set[Long] = Set.empty,
      skip: CursorPaging
    ): Future[Seq[RawOrder]] =
    orderDal.getOrdersForRecover(
      statuses,
      marketEntityIds,
      accountEntityIds,
      skip
    )

  def getCutoffAffectedOrders(
      retrieveCondition: RetrieveOrdersToCancel,
      take: Int
    ): Future[Seq[RawOrder]] =
    orderDal.getCutoffAffectedOrders(retrieveCondition, take)

  def getOrdersToActivate(
      activateLaggingInSecond: Int,
      limit: Int
    ): Future[Seq[RawOrder]] =
    orderDal.getOrdersToActivate(activateLaggingInSecond, limit)

  def getOrdersToExpire(
      expireLeadInSeconds: Int,
      limit: Int
    ): Future[Seq[RawOrder]] =
    orderDal.getOrdersToExpire(expireLeadInSeconds, limit)

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketId: Option[Long] = None,
      feeTokenSet: Option[String] = None
    ): Future[Int] =
    orderDal.countOrdersForUser(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketId,
      feeTokenSet
    )

  def updateOrderStatus(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode] = {
    orderDal.updateOrderStatus(hash, status)
  }

  def updateOrdersStatus(
      hashes: Seq[String],
      status: OrderStatus
    ): Future[ErrorCode] = {
    orderDal.updateOrdersStatus(hashes, status)
  }

  def updateOrderState(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] = orderDal.updateOrderState(hash, state)

  def updateAmounts(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] = orderDal.updateAmounts(hash, state)

  def cancelOrders(
      orderHashes: Seq[String],
      status: OrderStatus
    ): Future[Seq[UserCancelOrder.Res.Result]] =
    for {
      updated <- orderDal.updateOrdersStatus(orderHashes, status)
      selectOwners <- orderDal.getOrdersMap(orderHashes)
    } yield {
      if (updated == ErrorCode.ERR_NONE) {
        orderHashes.map { orderHash =>
          UserCancelOrder.Res.Result(
            orderHash,
            giveUserOrder(selectOwners.get(orderHash)),
            ErrorCode.ERR_NONE
          )
        }
      } else {
        throw ErrorException(
          ERR_INTERNAL_UNKNOWN,
          "Failed to update order status"
        )
      }
    }
}