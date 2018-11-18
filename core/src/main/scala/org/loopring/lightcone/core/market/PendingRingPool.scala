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

package org.loopring.lightcone.core.market

import org.loopring.lightcone.core.data._

trait PendingRingPool {
  def getOrderPendingAmountS(orderId: String): BigInt
  def deleteOrder(orderId: String): Boolean
  def deleteRing(ringId: String): Boolean

  def hasRing(ringId: String): Boolean
  def addRing(ring: OrderRing): Unit

  def deleteAllRings(): Unit
  def deleteRingsBefore(timestamp: Long): Unit
  def deleteRingsOlderThan(age: Long): Unit
  def deleteRingsContainingOrder(orderId: String): Unit
}

