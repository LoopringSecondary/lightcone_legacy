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
import org.loopring.lightcone.persistence.utils.SystemTimeProvider
import org.loopring.lightcone.proto.actors.{ XOrderState, XSaveOrderResult, XSaveOrderStateResult }
import org.loopring.lightcone.proto.core.{ XOrderStatus, XRawOrder }
import org.loopring.lightcone.proto.persistence._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class OrdersDalSpec extends DalSpec[OrderDal] {
  def getDal = new OrderDalImpl()

  "saveOrder" must "save a order with hash 0x111" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z saveOrder'
    var order = XRawOrder(hash = "0x111", version = 1, owner = "0x111", tokenS = "0x1", tokenB = "0x2", amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"), validSince = 999)
    val result: Future[XSaveOrderResult] = dal.saveOrder(order)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[XSaveOrderResult], 5.second)
    res.error should be(XPersistenceError.PERS_ERR_NONE)
  }

  "getOrdersByHash" must "get some orders's with hashes [0x111]" in {
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

  "getOrders" must "get some orders with many query parameters" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z getOrders'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_NEW).toSet
    val owners: Set[String] = Seq("0x111").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val result = dal.getOrders(Set.empty, owners, Set.empty, Set.empty, Set.empty, Some(XSort.ASC), None)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrder" must "get a order with hash 0x111" in {
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

  "getOrdersForUse" must "get some orders with many query parameters" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_NEW).toSet
    val owners: Set[String] = Seq("0x111").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val result = dal.getOrdersForUser(Set.empty, owners, Set.empty, Set.empty, Set.empty, Some(XSort.ASC), None)
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

  "countOrders" must "get orders count with many query parameters" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_NEW).toSet
    val owners: Set[String] = Seq("0x111").toSet
    val tokenSSet: Set[String] = Seq("0x1", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x2").toSet
    //val feeTokenSet: Set[String] = Seq("0x11", "0x22").toSet
    val result = dal.countOrders(Set.empty, owners, Set.empty, Set.empty, Set.empty)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be >= 0
  }

  "getOrdersForRecover" must "get some orders to recover" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z getOrders'
    val statuses: Set[XOrderStatus] = Seq(XOrderStatus.STATUS_NEW).toSet
    val owners: Set[String] = Seq("0x111").toSet
    val tokenSSet: Set[String] = Seq("0x11", "0x22").toSet
    val tokenBSet: Set[String] = Seq("0x11", "0x22").toSet
    val result = dal.getOrdersForRecover(statuses, owners, Set.empty, Set.empty, None, Some(XSort.ASC), None)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res should not be empty
  }

  "updateOrderStatus" must "update order's status with hash 0x111" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val result = dal.updateOrderStatus("0x111", XOrderStatus.STATUS_CANCELLED_BY_USER)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateAmount" must "update order's amount state with hash 0x111" in {
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
    val result = dal.updateAmount("0x111", state)
    result onComplete {
      case Success(r) ⇒ info("=== Success: " + r)
      case Failure(e) ⇒ info("=== Failed: " + e.getMessage)
    }
    val res = Await.result(result.mapTo[Either[XPersistenceError, String]], 5.second)
    res.isRight should be(true)
  }

  "updateFailed" must "update order's status to failed with hash 0x111" in {
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
