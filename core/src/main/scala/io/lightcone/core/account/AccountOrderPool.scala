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

trait AccountOrderPool {

  type Callback = Matchable => Unit

  def apply(id: String): Matchable
  def getOrder(id: String): Option[Matchable]
  def contains(id: String): Boolean
  def size: Int
  def orders: Iterable[Matchable]

  def addCallback(callback: Callback): Unit
  def removeCallback(callback: Callback): Unit

  def +=(order: Matchable): Unit
}

trait UpdatedOrdersTracing {
  self: AccountOrderPool =>
  private var updatedOrders = Map.empty[String, Matchable]

  addCallback((order: Matchable) => updatedOrders += order.id -> order)

  def getUpdatedOrders() = updatedOrders

  def clearUpdatedOrders() = this.synchronized {
    updatedOrders = Map.empty
  }

  def takeUpdatedOrders() = this.synchronized {
    val orders = getUpdatedOrders
    clearUpdatedOrders()
    orders
  }
}
