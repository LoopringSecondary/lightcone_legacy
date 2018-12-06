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
import org.loopring.lightcone.proto.actors.{ XSaveOrderResult, XSaveOrderStateResult }
import org.loopring.lightcone.proto.core.{ XRawOrder, _ }
import org.loopring.lightcone.proto.persistence.{ XOrderSortBy, XPersistenceError }

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }

class OrderStateDalSpec extends DalSpec[OrderStateDal] {
  val module = new TestDatabaseModule()
  val dal = new OrderStateDalImpl()

  "saveOrder" must "insert or update a order state" in {
    // sbt persistence/'testOnly *OrderStateDalSpec -- -z saveOrderState'
    var order = XOrderPersState(hash = "0x111", owner = "0x99", tokenS = "0x1", tokenB = "0x2", tokenFee = "0x000", amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"), amountFee = ByteString.copyFrom("199", "UTF-8"), validSince = 999)
    val result: Future[XSaveOrderStateResult] = dal.saveOrUpdate(order)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[XSaveOrderStateResult], 5.second)
    res.error should be(XPersistenceError.PERS_ERR_NONE)
  }

  "getOrdersByHash" must "get some orders's state" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    var orders = Seq("0x111")
    val result = dal.getOrders(orders)
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XOrderPersState]], 5.second)
    res should not be empty
  }

  "getAOrder" must "get a order state" in {
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
    val res = Await.result(result.mapTo[Option[XOrderPersState]], 5.second)
    res should not be empty
  }

  "getOrdersByUpdateAt" must "get some orders or empty" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x11", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sinceId: Option[Long] = Some(8000l)
    val tillId: Option[Long] = Some(9000l)
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
    val res = Await.result(result.mapTo[Seq[XOrderPersState]], 5.second)
    res should not be empty
  }

  "getOrders" must "get some orders" in {
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
    val result = dal.getOrders(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, Some(sortBy))
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XOrderPersState]], 5.second)
    res should not be empty
  }

  "getRangeOrders" must "get some orders" in {
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
    val result = dal.getRangeOrders(statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, Some(sortBy))
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XOrderPersState]], 5.second)
    res should not be empty
  }

  "countOrders" must "get orders count" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_CANCELLED_BY_USER, XOrderStatus.STATUS_CANCELLED_LOW_BALANCE).toSet
    val owners: Set[String] = Seq("0x111", "0x22").toSet
    val tokenSSet: Set[String] = Seq("0x1", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x2").toSet
    //val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val sinceId: Option[Long] = Some(8000l)
    val tillId: Option[Long] = Some(9000l)
    val sortedByUpdatedAt: Boolean = true
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
    val result = dal.updateOrderStatus("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER, false)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

}
