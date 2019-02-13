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

abstract class AccountManagerAltImplSpec extends CommonSpec {

  var owner = "owning_address"
  implicit val _ec: ExecutionContext = ExecutionContext.Implicits.global

  implicit val baProvider = stub[BalanceAndAllowanceProvider]

  implicit val updatdOrderProcessor = new UpdatedOrdersProcessor {
    val ec = _ec

    def processOrder(order: Matchable): Future[Any] =
      Future { processOneOrder(order) }

  }

  implicit var orderPool: AccountOrderPool with UpdatedOrdersTracing = _
  var manager: AccountManagerAlt = _

  var processOneOrder: Matchable => Unit = { order =>
    // println(s"==> order: $order")
  }

  def setSpendable(
      owner: String,
      token: String,
      spendable: BigInt
    ) =
    (baProvider.getBalanceAndALlowance _)
      .when(owner, token)
      .returns(Future.successful((spendable, spendable)))

  override def beforeEach(): Unit = {
    orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
    manager = AccountManagerAlt.default(owner)
  }
}
