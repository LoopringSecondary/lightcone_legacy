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

class AccountManagerAltImplSpec_Orders extends AccountManagerAltImplSpec {
  // import OrderStatus._

  "cancelling non-existing orders" should "return empty result" in {
    val (success, orderMap) = manager.cancelOrder("order0").await
    success should be(false)
    orderMap.size should be(0)

    var map = manager.cancelOrders(Seq("order0", "order1")).await
    map.size should be(0)

    map = manager.cancelOrders(LRC <-> WETH).await
    map.size should be(0)

    map = manager.cancelAllOrders().await
    map.size should be(0)
  }

  // "canceling existing orders" should work {

  // }

}
