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
import org.loopring.lightcone.proto.actors.XOrderState
import org.loopring.lightcone.proto.core.XRawOrder
import org.loopring.lightcone.proto.persistence.Bar
import org.loopring.lightcone.proto.core._
import org.web3j.utils.Numeric

import scala.concurrent.Await
import scala.concurrent.duration._

class OrdersDalSpec extends DalSpec[OrdersDal] {
  val dal = new OrdersDalImpl()

  "addOrder" must "insert a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    var order = XRawOrder(hash = "0x123")
    Await.result(dal.saveOrder(order), 5.second)
  }

  "getOrderByHashes" must "get a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    var orders = Seq("0x123", "456")
    Await.result(dal.getOrders(orders), 5.second)
  }

  "updateOrderStatusByHash" must "update a order" in {
    // sbt persistence/'testOnly *OrdersDalSpec -- -z addOrder'
    val state = XRawOrder.State(createdAt = 1l, updatedAt = 5l, matchedAt = 3l, updatedAtBlock = 4l, status = XOrderStatus.STATUS_CANCELLED_BY_USER,
      actualAmountS = ByteString.copyFrom("11", "UTF-8"), actualAmountB = ByteString.copyFrom("12", "UTF-8"),
      actualAmountFee = ByteString.copyFrom("13", "UTF-8"), outstandingAmountS = ByteString.copyFrom("14", "UTF-8"),
      outstandingAmountB = ByteString.copyFrom("15", "UTF-8"), outstandingAmountFee = ByteString.copyFrom("16", "UTF-8"))
    Await.result(dal.updateOrderState("0x123", state, false), 5.second)
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

    Await.result(dal.getOrders(100, statuses, owners, tokenSSet, tokenBSet, feeTokenSet, sinceId, tillId, true), 5.second)
  }
}
