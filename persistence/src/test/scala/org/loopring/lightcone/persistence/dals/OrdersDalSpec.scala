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
import org.loopring.lightcone.persistence.TestDatabaseModule
import org.loopring.lightcone.persistence.utils.SystemTimeProvider
import org.loopring.lightcone.proto.actors.{ XOrderState, XSaveOrderResult, XSaveOrderStateResult }
import org.loopring.lightcone.proto.core.{ XOrderStatus, XRawOrder }
import org.loopring.lightcone.proto.persistence.{ XOrderSortBy, XPersistenceError }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class OrdersDalSpec extends DalSpec[OrderDal] {
  val module = new TestDatabaseModule()
  def getDal = new OrderDalImpl()

  "batchSaveOrder" must "try insert some orders" in {
    val result = for (i ← 0 to 100) {
      val order = XRawOrder(hash = "0x11" + i, version = 1, owner = "0x99", tokenS = "0x1", tokenB = "0x2", amountS = ByteString.copyFrom("11", "UTF-8"),
        amountB = ByteString.copyFrom("12", "UTF-8"), validSince = 999)
      dal.saveOrder(order)
    }
    Thread.sleep(5000)
  }

  "saveOrder" must "insert a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z saveOrder'
    var order = XRawOrder(hash = "0x112", version = 1, owner = "0x99", tokenS = "0x1", tokenB = "0x2", amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"), validSince = 999)
    val result: Future[XSaveOrderResult] = dal.saveOrder(order)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[XSaveOrderResult], 5.second)
    res.error should be(XPersistenceError.PERS_ERR_NONE)
  }

  "getOrdersByHash" must "get some orders's state" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.getOrders(Seq("0x111"))
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrders" must "get some orders" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z getOrders'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x11", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sortedByUpdatedAt: Boolean = true
    val sortBy = if (sortedByUpdatedAt) {
      XOrderSortBy().withUpdatedAt(true)
    } else {
      XOrderSortBy().withCreatedAt(true)
    }
    val result = dal.getOrders(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, Some(sortBy))
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrder" must "get a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.getOrder("0x111")
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrdersByUpdateAt" must "get some orders or empty" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x11", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sortedByUpdatedAt: Boolean = false
    val sortBy = if (sortedByUpdatedAt) {
      XOrderSortBy().withUpdatedAt(true)
    } else {
      XOrderSortBy().withCreatedAt(true)
    }
    val result = dal.getOrdersByUpdatedAt(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, Some(1000l), Some(99999l), Some(sortBy))
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrdersBySequence" must "get some orders" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z getOrders'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x11", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sinceId: Option[Long] = Some(8000l)
    val tillId: Option[Long] = Some(9000l)
    val sortedByUpdatedAt: Boolean = true
    val sortBy = if (sortedByUpdatedAt) {
      XOrderSortBy().withUpdatedAt(true)
    } else {
      XOrderSortBy().withCreatedAt(true)
    }
    val result = dal.getOrdersBySequence(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, Some(sortBy))
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "countOrders" must "get orders count" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x111", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x1", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x2").toSet
    //val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sinceId: Option[Long] = Some(1l)
    val tillId: Option[Long] = Some(9000l)
    val result = dal.countOrders(statuses, owners, tokenSSet, tokenBSet, Set.empty, sinceId, tillId)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be >= 0
  }

  "updateOrderStatus" must "update a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.updateOrderStatus("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER, true)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateAmount" must "update a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
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
    val result = dal.updateAmount("0x111", state, true)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateFailed" must "update a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.updateFailed("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }
}
