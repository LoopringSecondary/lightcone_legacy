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
import org.loopring.lightcone.proto.actors.{ XOrderState, XSaveOrderResult, XSaveOrderStateResult }
import org.loopring.lightcone.proto.core.XRawOrder
import org.loopring.lightcone.proto.persistence.{ XOrderSortBy, XPersistenceError }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.concurrent.duration._

class OrdersDalSpec extends DalSpec[OrderDal] {
  val module = new TestDatabaseModule()
  val dal = new OrderDalImpl(module)

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

  "getOrders" must "get some orders" in {
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

}
