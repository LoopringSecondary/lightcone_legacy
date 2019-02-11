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

package io.lightcone.core

private[core] final class ReserveManagerAltAutoCancelImpl(
  )(
    implicit
    token: String)
    extends ReserveManagerAltImplBase {

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = this.synchronized {
    var ordersToDelete = Set.empty[String]
    var idx = reserves.indexWhere(_.orderId == orderId)

    def insuffcient(additonal: BigInt = 0) =
      spendable - reserved + additonal < requestedAmount

    if (idx >= 0) {
      // this is an existing order to scale down/up
      // we release the old reserve first
      val reserve = reserves(idx)
      reserved -= reserve.reserved

      // get the total reserved amount by all orders elder than this one
      val sum = reserves.take(idx).map(_.reserved).sum

      // println("=======", idx, reserved, sum)
      if (insuffcient(sum)) {
        // releaseing all orders prior to this order still ends up low reserve
        ordersToDelete += orderId
        reserves = reserves.patch(idx, Nil, 1)
      } else {
        while (insuffcient()) {
          ordersToDelete += reserves.head.orderId
          reserved -= reserves.head.reserved
          reserves = reserves.tail
          idx -= 1
        }

        assert(idx >= 0)
        reserved += requestedAmount
        val reserve = Reserve(orderId, requestedAmount)
        reserves = reserves.patch(idx, Seq(reserve), 1)
      }

    } else {
      // this is a new order
      if (spendable < requestedAmount) {
        // not enough spendable for this order
        ordersToDelete += orderId
      } else {
        while (insuffcient()) {
          val first = reserves.head
          ordersToDelete += first.orderId
          reserved -= first.reserved
          reserves = reserves.tail
        }
        reserved += requestedAmount
        reserves = reserves :+ Reserve(orderId, requestedAmount)
      }
    }

    ordersToDelete
  }

}
