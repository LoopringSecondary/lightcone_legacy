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

package io.lightcone.actors.support

import java.util.concurrent.TimeUnit
import akka.pattern._
import io.lightcone.actors.core._
import io.lightcone.actors.validator._
import io.lightcone.proto._
import io.lightcone.core._
import org.rnorth.ducttape.TimeoutException
import org.rnorth.ducttape.unreliables.Unreliables
import org.testcontainers.containers.ContainerLaunchException
import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}

trait OrderbookManagerSupport
    extends MetadataManagerSupport
    with DatabaseModuleSupport
    with MarketManagerSupport {
  my: CommonSpec with EthereumSupport =>

  def startOrderbookSupport() = {
    actors.add(OrderbookManagerActor.name, OrderbookManagerActor.start)

    actors.add(
      OrderbookManagerMessageValidator.name,
      MessageValidationActor(
        new OrderbookManagerMessageValidator(),
        OrderbookManagerActor.name,
        OrderbookManagerMessageValidator.name
      )
    )

    try Unreliables.retryUntilTrue(
      10,
      TimeUnit.SECONDS,
      () => {
        val f = Future.sequence(metadataManager.getValidMarketPairs.values.map {
          marketPair =>
            val orderBookInit = GetOrderbook.Req(0, 100, Some(marketPair))
            actors.get(OrderbookManagerActor.name) ? orderBookInit
        })
        val res =
          Await.result(f.mapTo[Seq[GetOrderbook.Res]], timeout.duration)
        res.nonEmpty
      }
    )
    catch {
      case e: TimeoutException =>
        throw new ContainerLaunchException(
          "Timed out waiting for connectionPools init.)"
        )
    }

    // TODO：因暂时未完成recover，因此需要发起一次请求，将shard初始化成功
    metadataManager.getValidMarketPairs.values.map { marketPair =>
      val orderBookInit = GetOrderbook.Req(0, 100, Some(marketPair))
      val orderBookInitF = actors.get(OrderbookManagerActor.name) ? orderBookInit
      Await.result(orderBookInitF, timeout.duration)
    }
  }

  startOrderbookSupport()

}
