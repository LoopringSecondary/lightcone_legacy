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
import org.loopring.lightcone.proto.XErrorCode.ERR_INTERNAL_UNKNOWN
import org.loopring.lightcone.proto._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import scala.concurrent._

class OrderServiceImpl @Inject()(
    implicit
    val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext)
    extends OrderService {
  val orderDal: OrderDal = new OrderDalImpl()
  val timeProvider = new SystemTimeProvider()

  private def giveUserOrder(order: Option[XRawOrder]): Option[XRawOrder] = {
    order match {
      case Some(o) =>
        val state = o.state.get
        val returnState =
          XRawOrder.State(status = state.status, createdAt = state.createdAt)
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
  def saveOrder(order: XRawOrder): Future[Either[XRawOrder, XErrorCode]] = {
    val now = timeProvider.getTimeMillis
    val state = XRawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = XOrderStatus.STATUS_NEW
    )
    val o = order.copy(
      state = Some(state),
      marketHash = MarketHashProvider.convert2Hex(order.tokenS, order.tokenB),
      marketHashId = 0,
      addressShardId = Math.abs(order.owner.hashCode % 100)
    )
    orderDal.saveOrder(o).map { r =>
      if (r.error == XErrorCode.ERR_NONE) {
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
        XOrderStatus.STATUS_CANCELLED_BY_USER
      )
      selectOwners <- orderDal.getOrdersMap(orderHashes)
    } yield {
      if (updated == XErrorCode.ERR_NONE) {
        orderHashes.map { orderHash =>
          XUserCancelOrderResult.Result(
            orderHash,
            giveUserOrder(selectOwners.get(orderHash)),
            XErrorCode.ERR_NONE
          )
        }
      } else {
        throw ErrorException(ERR_INTERNAL_UNKNOWN, "failed to update")
      }
    }

  def getOrders(hashes: Seq[String]): Future[Seq[XRawOrder]] =
    orderDal.getOrders(hashes)

  def getOrder(hash: String): Future[Option[XRawOrder]] =
    orderDal.getOrder(hash)

  def getOrders(
      statuses: Set[XOrderStatus],
      owners: Set[String],
      tokenSSet: Set[String],
      tokenBSet: Set[String],
      marketHashSet: Set[String],
      feeTokenSet: Set[String],
      sort: Option[XSort],
      skip: Option[XSkip]
    ): Future[Seq[XRawOrder]] =
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
      statuses: Set[XOrderStatus],
      owner: Option[String] = None,
      tokenS: Option[String] = None,
      tokenB: Option[String] = None,
      marketHashSet: Option[String] = None,
      feeTokenSet: Option[String] = None,
      sort: Option[XSort] = None,
      skip: Option[XSkip] = None
    ): Future[Seq[XRawOrder]] =
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
      statuses: Set[XOrderStatus],
      owners: Set[String],
      tokenSSet: Set[String],
      tokenBSet: Set[String],
      marketHashSet: Set[String],
      validTime: Option[Int],
      sort: Option[XSort],
      skip: Option[XSkip]
    ): Future[Seq[XRawOrder]] =
    orderDal.getOrdersForRecover(
      statuses,
      owners,
      tokenSSet,
      tokenBSet,
      marketHashSet,
      validTime,
      sort,
      skip
    )

  // Count the number of orders
  def countOrdersForUser(
      statuses: Set[XOrderStatus],
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

  def countOrdersForRecover(
      statuses: Set[XOrderStatus],
      owners: Set[String],
      tokenSSet: Set[String],
      tokenBSet: Set[String],
      marketHashSet: Set[String],
      feeTokenSet: Set[String]
    ): Future[Int] =
    orderDal.countOrdersForRecover(
      statuses,
      owners,
      tokenSSet,
      tokenBSet,
      marketHashSet,
      feeTokenSet
    )

  def updateOrderStatus(
      hash: String,
      status: XOrderStatus
    ): Future[XErrorCode] = {
    orderDal.updateOrderStatus(hash, status)
  }

  def updateAmount(
      hash: String,
      state: XRawOrder.State
    ): Future[XErrorCode] = orderDal.updateAmount(hash, state)
}
