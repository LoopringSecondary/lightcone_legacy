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

import com.google.protobuf.ByteString
import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.proto.actors.{ XOrderState, XSaveOrderResult, XSaveOrderStateResult }
import org.loopring.lightcone.proto.core.{ XOrderStatus, XRawOrder }
import org.loopring.lightcone.proto.persistence._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._

class OrderDalSpec extends DalSpec[OrderDal] {
  def getDal = new OrderDalImpl()

  private def testSave(): Future[XSaveOrderResult] = {
    var order = XRawOrder(
      owner = "0x111",
      hash = "0x111",
      version = 1,
      tokenS = "0x1",
      tokenB = "0x2",
      amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"),
      validSince = 999
    )
    dal.saveOrder(order)
  }

  "saveOrder" must "save a order with hash 0x111" in {
    val result = for {
      _ ← testSave()
      query ← dal.getOrder("0x111")
    } yield query
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrders" must "get some orders with many query parameters" in {
    val result = for {
      _ ← testSave()
      query ← dal.getOrders(Set.empty, Set("0x111"), Set.empty, Set.empty, Set.empty, Some(XSort.ASC), None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrder" must "get a order with hash 0x111" in {
    val result = for {
      _ ← testSave()
      query ← dal.getOrder("0x111")
    } yield query
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrdersForUse" must "get some orders with many query parameters" in {
    val result = for {
      _ ← testSave()
      query ← dal.getOrdersForUser(Set.empty, Set("0x111"), Set.empty, Set.empty, Set.empty, Some(XSort.ASC), None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "countOrders" must "get orders count with many query parameters" in {
    val result = for {
      _ ← testSave()
      query ← dal.countOrders(Set.empty, Set("0x111"), Set.empty, Set.empty, Set.empty)
    } yield query
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be >= 0
  }

  "getOrdersForRecover" must "get some orders to recover" in {
    val result = for {
      _ ← testSave()
      query ← dal.getOrdersForRecover(Set(XOrderStatus.STATUS_NEW), Set("0x111"), Set.empty, Set.empty, None, Some(XSort.ASC), None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "updateOrderStatus" must "update order's status with hash 0x111" in {
    val result = for {
      _ ← testSave()
      query ← dal.updateOrderStatus("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER)
    } yield query
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateAmount" must "update order's amount state with hash 0x111" in {
    val timeProvider = new SystemTimeProvider()
    val now = timeProvider.getTimeMillis
    val state = XRawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = XOrderStatus.STATUS_NEW,
      actualAmountB = ByteString.copyFrom("111", "UTF-8"),
      actualAmountS = ByteString.copyFrom("112", "UTF-8"),
      actualAmountFee = ByteString.copyFrom("113", "UTF-8"),
      outstandingAmountB = ByteString.copyFrom("114", "UTF-8"),
      outstandingAmountS = ByteString.copyFrom("115", "UTF-8"),
      outstandingAmountFee = ByteString.copyFrom("116", "UTF-8")
    )
    val result = for {
      _ ← testSave()
      query ← dal.updateAmount("0x111", state)
    } yield query
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateFailed" must "update order's status to failed with hash 0x111" in {
    val result = for {
      _ ← testSave()
      query ← dal.updateFailed("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER)
    } yield query
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }
}
