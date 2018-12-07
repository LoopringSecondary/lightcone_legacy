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

package org.loopring.lightcone.gateway.service

import akka.pattern._
import org.loopring.lightcone.proto.actors._

import scala.concurrent.Future

trait AccountService {
  self: ApiService ⇒

  //todo：是否与actors等使用同一个proto，或者重新定义在gateway.proto
  def getBalanceAndAllowance(req: XGetBalanceAndAllowancesReq): Future[XGetBalanceAndAllowancesRes] =
    (self.entryPointActor ? req).mapTo[XGetBalanceAndAllowancesRes]

}
