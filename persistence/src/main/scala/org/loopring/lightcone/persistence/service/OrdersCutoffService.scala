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

package org.loopring.lightcone.persistence.service

import org.loopring.lightcone.persistence.dals.OrdersCutoffDal
import org.loopring.lightcone.proto.{ErrorCode, OrdersCutoffEvent}
import scala.concurrent.Future

trait OrdersCutoffService {
  val cutoffDal: OrdersCutoffDal

  def saveCutoff(cutoff: OrdersCutoffEvent): Future[ErrorCode]

  def hasCutoff(
      orderBroker: Option[String] = None,
      orderOwner: String,
      orderTradingPair: String,
      time: Long // in seconds, where cutoff > time
    ): Future[Boolean]

  def obsolete(height: Long): Future[Unit]
}
