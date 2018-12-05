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
import org.loopring.lightcone.proto.actors.{ XOrderState, XSaveOrderResult }
import org.loopring.lightcone.proto.core.XRawOrder
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.persistence.{ XOrderSortBy, XPersistenceError }
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class OrdersDalSpec extends DalSpec[OrderDal] {
  val dal = new OrderDalImpl()

  "addOrder" must "insert a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    var order = XRawOrder(hash = "0x111", version = 1, owner = "0x99", tokenS = "0x1", tokenB = "0x2", amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"), validSince = 999)
    val result: Future[XSaveOrderResult] = dal.saveOrder(order)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[XSaveOrderResult], 5.second)
    res.error should be(XPersistenceError.PERS_ERR_NONE)
  }

  "getOrderByHashes" must "get a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    var orders = Seq("0x111")
    val result = dal.getOrdersByHash(orders)
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
          info("status:" + order.state.get.status)
          info("actualAmountB:" + order.state.get.actualAmountB.toStringUtf8 + " actualAmountS:" + order.state.get.actualAmountS.toStringUtf8 + " actualAmountFee:" + order.state.get.actualAmountFee.toStringUtf8
            + "outAmountB:" + order.state.get.outstandingAmountB.toStringUtf8 + " outAmountS:" + order.state.get.outstandingAmountS.toStringUtf8 + " outAmountFee:" + order.state.get.outstandingAmountFee.toStringUtf8)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "updateOrderState" must "update a order state" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val state = XRawOrder.State(createdAt = 1l, updatedAt = 5l, matchedAt = 3l, updatedAtBlock = 4l, status = XOrderStatus.STATUS_CANCELLED_BY_USER,
      actualAmountS = ByteString.copyFrom("888", "UTF-8"), actualAmountB = ByteString.copyFrom("999", "UTF-8"),
      actualAmountFee = ByteString.copyFrom("13", "UTF-8"), outstandingAmountS = ByteString.copyFrom("14", "UTF-8"),
      outstandingAmountB = ByteString.copyFrom("15", "UTF-8"), outstandingAmountFee = ByteString.copyFrom("16", "UTF-8"))
    val result = dal.updateOrderState("0x111", state)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateOrderStatus" must "update a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.updateOrderStatus("0x111", XOrderStatus.STATUS_NEW, true)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "getOrders" must "get some orders" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
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
    val result = dal.getOrders(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, Some(sortBy))
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

  "getOrderByUpdateAt" must "get some orders or empty" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
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
    val result = dal.getOrdersByUpdatedAt(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, Some(1000l), Some(99999l), Some(sortBy))
    result onComplete {
      case Success(orders) ⇒ {
        orders.foreach { order ⇒
          info("order:" + order)
          info("status:" + order.state.get.status)
          info("actualAmountB:" + order.state.get.actualAmountB.toStringUtf8 + " actualAmountS:" + order.state.get.actualAmountS.toStringUtf8 + " actualAmountFee:" + order.state.get.actualAmountFee.toStringUtf8
            + "outAmountB:" + order.state.get.outstandingAmountB.toStringUtf8 + " outAmountS:" + order.state.get.outstandingAmountS.toStringUtf8 + " outAmountFee:" + order.state.get.outstandingAmountFee.toStringUtf8)
        }
      }
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }
}
