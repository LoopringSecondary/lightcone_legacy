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
import org.loopring.lightcone.persistence.dals.{
  OrdersCancelledEventDal,
  OrdersCancelledEventDalImpl
}
import org.loopring.lightcone.proto.{ErrorCode, XOrdersCancelledEvent}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class OrdersCancelledEventServiceImpl @Inject()(
    implicit val dbConfig: DatabaseConfig[JdbcProfile],
    @Named("db-execution-context") val ec: ExecutionContext)
    extends OrdersCancelledEventService {

  val ordersCancelledEventDal: OrdersCancelledEventDal =
    new OrdersCancelledEventDalImpl()

  def saveCancelOrder(cancelOrder: XOrdersCancelledEvent): Future[ErrorCode] =
    ordersCancelledEventDal.saveCancelOrder(cancelOrder)

  def hasCancelled(orderHash: String): Future[Boolean] =
    ordersCancelledEventDal.hasCancelled(orderHash)

  def obsolete(height: Long): Future[Unit] =
    ordersCancelledEventDal.obsolete(height)
}
