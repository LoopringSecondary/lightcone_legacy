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

package org.loopring.lightcone.actors.support

import akka.pattern._
import org.loopring.lightcone.actors.core.OrderbookManagerActor
import org.loopring.lightcone.actors.validator._
import org.loopring.lightcone.ethereum.data.{Address => LAddress}
import org.loopring.lightcone.proto._

import scala.collection.JavaConverters._
import scala.concurrent.Await

trait OrderbookManagerSupport {
  my: CommonSpec =>
  actors.add(OrderbookManagerActor.name, OrderbookManagerActor.start)

  actors.add(
    OrderbookManagerMessageValidator.name,
    MessageValidationActor(
      new OrderbookManagerMessageValidator(),
      OrderbookManagerActor.name,
      OrderbookManagerMessageValidator.name
    )
  )

  //todo：因暂时未完成recover，因此需要发起一次请求，将shard初始化成功
  config
    .getObjectList("markets")
    .asScala
    .map { item =>
      val c = item.toConfig
      val marketId = MarketId(
        LAddress(c.getString("primary")).toString,
        LAddress(c.getString("secondary")).toString
      )
      val orderBookInit = GetOrderbook.Req(0, 100, Some(marketId))
      val orderBookInitF = actors.get(OrderbookManagerActor.name) ? orderBookInit
      Await.result(orderBookInitF, timeout.duration)
    }

}
