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

private[core] final class ReserveManagerAltClassicImpl(
    val token: String
  )(
    implicit
    val eventHandler: ReserveEventHandler)
    extends ReserveManagerAltImplBase {

  def reserve(
      orderId: String,
      requestedAmount: BigInt
    ): Set[String] = this.synchronized {
    var ordersToDelete = Set.empty[String]

    def insuffcient() = spendable - reserved < requestedAmount

    var idx = reserves.indexWhere(_.orderId == orderId)
    if (idx >= 0) {
      // this is an existing order to scale down/up
      // we release the old reserve first
      val reserve = reserves(idx)
      reserved -= reserve.reserved

      if (insuffcient()) {
        // not enough spendable for this order
        ordersToDelete += orderId
        reserves = reserves.patch(idx, Nil, 1)
      } else {
        reserved += requestedAmount
        val reserve = Reserve(orderId, requestedAmount)
        reserves = reserves.patch(idx, Seq(reserve), 1)
        eventHandler.onTokenReservedForOrder(orderId, token, requestedAmount)
      }

    } else {
      // this is a new order
      if (insuffcient()) {
        ordersToDelete += orderId
      } else {
        reserved += requestedAmount
        reserves = reserves :+ Reserve(orderId, requestedAmount)
        eventHandler.onTokenReservedForOrder(orderId, token, requestedAmount)
      }
    }
    ordersToDelete
  }
}
