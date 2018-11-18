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

package org.loopring.lightcone.core.account
import org.loopring.lightcone.core.data._

import org.slf4s.Logging

trait AccountOrderPool {

  type Callback = Order ⇒ Unit

  def apply(id: String): Order
  def getOrder(id: String): Option[Order]
  def contains(id: String): Boolean
  def size: Int

  def addCallback(callback: Callback): Unit
  def removeCallback(callback: Callback): Unit

  def +=(order: Order): Unit
}

trait UpdatedOrdersTracing {
  self: AccountOrderPool ⇒
  private var updatedOrders = Map.empty[String, Order]

  def getUpdatedOrders() = updatedOrders.values
  def clearUpdatedOrders() = { updatedOrders = Map.empty }
  def takeUpdatedOrders() = {
    val orders = getUpdatedOrders
    clearUpdatedOrders()
    orders
  }
}

trait AccountOrderPoolWithUpdatedOrdersTracing
  extends AccountOrderPool
  with UpdatedOrdersTracing
