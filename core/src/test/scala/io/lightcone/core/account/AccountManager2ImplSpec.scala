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

import scala.concurrent._
import io.lightcone.core.testing._

abstract class AccountManagerAltImplSpec_Base extends CommonSpec {

  var owner = "owning_address"
  var manager: AccountManagerAlt = _

  var balanceMap =
    Map.empty[String, (BigInt /*balance*/, BigInt /*allowance*/ )]

  def processOneOrder(order: Matchable): Unit = println(s"==> order: $order")

  implicit val _ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit val orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing

  implicit val baProvider = new BalanceAndAllowanceProvider {

    def getBalanceAndALlowance(
        address: String,
        token: String
      ): Future[(BigInt, BigInt)] = Future.successful {
      balanceMap.getOrElse(token, (BigInt(0), BigInt(0)))
    }
  }

  implicit val updatdOrderProcessor = new UpdatedOrdersProcessor {
    val ec = _ec

    def processOrder(order: Matchable): Future[Any] = {
      processOneOrder(order)
      Future.unit
    }

  }

  override def beforeEach(): Unit = {
    manager = AccountManagerAlt.default(owner)
  }
}
