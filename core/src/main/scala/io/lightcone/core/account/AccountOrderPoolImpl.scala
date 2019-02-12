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

import org.slf4s.Logging

class AccountOrderPoolImpl() extends AccountOrderPool with Logging {

  private var callbacks = Set.empty[Callback]
  private[core] var orderMap = Map.empty[String, Matchable]

  def apply(id: String) = orderMap(id)
  def getOrder(id: String) = orderMap.get(id)
  def contains(id: String) = orderMap.contains(id)
  def size: Int = orderMap.size
  def orders() = orderMap.values

  def addCallback(callback: Callback) = this.synchronized {
    callbacks += callback
  }

  def removeCallback(callback: Callback) = this.synchronized {
    callbacks -= callback
  }

  def +=(order: Matchable) = this.synchronized {
    getOrder(order.id) match {
      case Some(existing) if existing == order =>
      case _ =>
        order.status match {
          case OrderStatus.STATUS_NEW =>
            add(order.id, order)

          case OrderStatus.STATUS_PENDING =>
            add(order.id, order)
            callbacks.foreach(_(order))

          case _ =>
            // log.debug("drop_order_from_pool: " + order)
            delete(order.id)
            callbacks.foreach(_(order))
        }
    }
  }

  override def toString() = orderMap.toString

  private def add(
      id: String,
      order: Matchable
    ): Unit = orderMap += id -> order
  private def delete(id: String): Unit = orderMap -= id

}
