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

import io.lightcone.core.OrderStatus._

private[core] class RichOrderStatus(status: OrderStatus) {

  def isCancelledStatus(): Boolean = {
    status match {
      case STATUS_EXPIRED | //
          STATUS_DUST_ORDER | //
          STATUS_COMPLETELY_FILLED | //
          STATUS_SOFT_CANCELLED_BY_USER | //
          STATUS_SOFT_CANCELLED_BY_USER_TRADING_PAIR | //
          STATUS_ONCHAIN_CANCELLED_BY_USER | //
          STATUS_ONCHAIN_CANCELLED_BY_USER_TRADING_PAIR | //
          STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES | //
          STATUS_SOFT_CANCELLED_LOW_BALANCE | //
          STATUS_SOFT_CANCELLED_LOW_FEE_BALANCE | //
          STATUS_SOFT_CANCELLED_TOO_MANY_ORDERS | //
          STATUS_SOFT_CANCELLED_DUPLICIATE =>
        true

      case _ =>
        false
    }
  }
}
