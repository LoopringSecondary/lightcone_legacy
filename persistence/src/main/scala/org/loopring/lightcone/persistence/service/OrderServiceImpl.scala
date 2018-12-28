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
import org.loopring.lightcone.lib.{
  ErrorException,
  MarketHashProvider,
  SystemTimeProvider
}
import org.loopring.lightcone.persistence.dals.{OrderDal, OrderDalImpl}
import org.loopring.lightcone.proto.ErrorCode.ERR_INTERNAL_UNKNOWN
import org.loopring.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

class OrderServiceImpl @Inject()(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext)
    extends OrderService {
  val orderDal: OrderDal = new OrderDalImpl()
  val timeProvider = new SystemTimeProvider()

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
            marketHash = "",
            marketHashId = 0,
            addressShardId = 0
          )
        )
      case None => None
    }
  }

  // Save order to database, if the order already exist, return an error code.
  def saveOrder(order: RawOrder): Future[Either[RawOrder, ErrorCode]] = {
    if (order.addressShardId < 0 || order.marketHashId <= 0) {
      throw ErrorException(
        ErrorCode.ERR_INTERNAL_UNKNOWN,
        s"Invalid addressShardId:[${order.addressShardId}] or marketHashId:[${order.marketHashId}]"
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

  // Mark the order as soft-cancelled. Returns error code if the order does not exist.
  def markOrderSoftCancelled(
      orderHashes: Seq[String]
    ): Future[Seq[XUserCancelOrderResult.Result]] =
    for {
      updated <- orderDal.updateOrdersStatus(
        orderHashes,
        OrderStatus.STATUS_CANCELLED_BY_USER
      )
      selectOwners <- orderDal.getOrdersMap(orderHashes)
    } yield {
      if (updated == ErrorCode.ERR_NONE) {
        orderHashes.map { orderHash =>
          XUserCancelOrderResult.Result(
            orderHash,
            giveUserOrder(selectOwners.get(orderHash)),
            ErrorCode.ERR_NONE
          )
        }
      } else {
        throw ErrorException(ERR_INTERNAL_UNKNOWN, "failed to update")
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
      marketHashSet: Set[String],
      feeTokenSet: Set[String],
      sort: Option[SortingType],
      skip: Option[XSkip]
    ): Future[Seq[RawOrder]] =
    orderDal
      .getOrders(
        statuses,
        owners,
        tokenSSet,
        tokenBSet,
        marketHashSet,
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
      marketHashSet: Option[String] = None,
      feeTokenSet: Option[String] = None,
      sort: Option[SortingType] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[RawOrder]] =
    orderDal
      .getOrdersForUser(
        statuses,
        owner,
        tokenS,
        tokenB,
        marketHashSet,
        feeTokenSet,
        sort,
        skip
      )
      .map(_.map(r => giveUserOrder(Some(r)).get))

  def getOrdersForRecover(
      statuses: Set[OrderStatus],
      marketHashIdSet: Set[Int] = Set.empty,
      addressShardIdSet: Set[Int] = Set.empty,
      skip: XSkipBySequenceId
    ): Future[Seq[RawOrder]] =
    orderDal.getOrdersForRecover(
      statuses,
      marketHashIdSet,
      addressShardIdSet,
      skip
    )

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[OrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHash: Option[String] = None,
      feeTokenSet: Option[String] = None
    ): Future[Int] =
    orderDal.countOrdersForUser(
      statuses,
      owner,
      tokenS,
      tokenB,
      marketHash,
      feeTokenSet
    )

  def updateOrderStatus(
      hash: String,
      status: OrderStatus
    ): Future[ErrorCode] = {
    orderDal.updateOrderStatus(hash, status)
  }

  def updateAmount(
      hash: String,
      state: RawOrder.State
    ): Future[ErrorCode] = orderDal.updateAmount(hash, state)
}
