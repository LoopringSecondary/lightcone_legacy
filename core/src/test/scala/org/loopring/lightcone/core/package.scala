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

package org.loopring.lightcone

import org.loopring.lightcone.core.data._
import org.loopring.lightcone.proto._

package object core {
  implicit class RichDoubleValue(v: Double) {

    def toWei(tokenAddr: String)(implicit tm: base.TokenManager) = {
      tm.getToken(tokenAddr).toWei(v)
    }

    def ! = BigInt(v.toLong)
  }

  implicit class RichMatchable(order: Matchable) {
    def asPending() = order.copy(status = XOrderStatus.STATUS_PENDING)
    def withActualAsOriginal() = order.copy(_actual = Some(order.original))
    def withMatchableAsActual() = order.copy(_matchable = Some(order.actual))
    def matchableAsOriginal() = order.copy(_matchable = Some(order.original))
  }
}
