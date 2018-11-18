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

package org.loopring.lightcone.auxiliary.order

import com.google.inject.Inject
import org.loopring.lightcone.proto.auxiliary._
import org.loopring.lightcone.auxiliary.model._

class OrderWriteHelperImpl @Inject() (validator: OrderValidator) extends OrderWriteHelper {

  val MAX_SOFT_CANCEL_INTERVAL: Long = 60 * 10 // 10 minute

  override def generateHash(order: Order): String = ???
  override def fillInOrder(order: Order): Order = {
    val essential = order.rawOrder.rawOrderEssential.copy(hash = generateHash(order))
    val filledRawOrder = order.rawOrder.copy(rawOrderEssential = essential)
    order.copy(rawOrder = filledRawOrder, market = getMarket(order), side = getSide(order).toString, price = getPrice(order))
  }

  override def validateOrder(order: Order): ValidateResult = validator.validate(order)

  override def isOrderExist(order: Order): Boolean = ???

  override def getMarket(order: Order): String = ???

  override def getSide(order: Order): MarketSide = ???

  override def getPrice(order: Order): Double = ???

  override def validateSoftCancelSign(optSign: Option[SoftCancelSign]): ValidateResult = optSign match {
    case None ⇒
      ValidateResult(false, "sign context is empty")
    case Some(v) if v.owner.isEmpty ⇒
      ValidateResult(false, "owner must be applied")
    case Some(v) if math.abs(System.currentTimeMillis / 1000 - v.timestamp) > MAX_SOFT_CANCEL_INTERVAL ⇒
      ValidateResult(false, "timestamp had expired")
    //TODO(xiaolu) confirm with fukun getPublicAddress from sig
    //    case Some(v) if !v.owner.equalsIgnoreCase(v.getSignerAddr()) ⇒
    //      ValidateResult(false, "sign address is not match")
    case _ ⇒
      ValidateResult(true)
  }
}
