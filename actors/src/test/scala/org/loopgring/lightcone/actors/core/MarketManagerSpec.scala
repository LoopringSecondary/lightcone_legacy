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

package org.loopgring.lightcone.actors.core

import akka.actor._
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.loopring.lightcone.actors.Routers
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth.OrderAwareOrderbookAggregatorImpl
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors.XOrder
import org.loopring.lightcone.proto.core.{ XMarketId, XMarketManagerConfig }
import org.scalatest._

import scala.concurrent.duration._

class MarketManagerSpec() extends TestKit(ActorSystem("marketManagerSpec", ConfigFactory.load())) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val marketId = XMarketId()
  implicit val tmm = new TokenMetadataManager()
  implicit val tve = new TokenValueEstimator()
  implicit val ringIncomeEstimator = new RingIncomeEstimatorImpl()
  implicit val timeProvider = new SystemTimeProvider()
  val config = XMarketManagerConfig()

  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()
  val dustOrderEvaluator = new DustOrderEvaluator()
  val aggregator = new OrderAwareOrderbookAggregatorImpl(config.priceDecimals)
  var marketManager = new MarketManagerImpl(
    marketId,
    config,
    tmm,
    ringMatcher,
    pendingRingPool,
    dustOrderEvaluator,
    aggregator
  )
  implicit val ec = system.dispatcher
  implicit val timeout = Timeout(5 second)
  implicit val routers = new Routers()

  val ethereumAccessActor = TestActorRef(TestActors.blackholeProps)
  routers.actors += GasPriceProviderActor.name → system.actorOf(Props(new GasPriceProviderActor()))

  val marketManagerActor = system.actorOf(Props(new MarketManagerActor(marketManager)))

  "submitOrder" must {

    "send orderUpdateEvent to orderbookManagerActor" in {

    }

  }
}
