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

import org.loopring.lightcone.proto.auxiliary._
import org.loopring.lightcone.auxiliary.model._

trait OrderWriteHelper {
  def generateHash(order: Order): String
  def fillInOrder(order: Order): Order
  def validateOrder(order: Order): ValidateResult
  def isOrderExist(order: Order): Boolean
  def getMarket(order: Order): String
  def getSide(order: Order): XMarketSide
  def getPrice(order: Order): Double
  def validateSoftCancelSign(optSign: Option[SoftCancelSign]): ValidateResult
}
