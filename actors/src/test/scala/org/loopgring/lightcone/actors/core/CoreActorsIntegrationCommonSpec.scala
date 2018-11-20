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
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.core._
import org.loopring.lightcone.proto.deployment._
import org.scalatest._
import org.loopring.lightcone.actors.data._
import scala.concurrent.duration._

abstract class CoreActorsIntegrationCommonSpec
  extends TestKit(ActorSystem("test"))
  with ImplicitSender
  with Matchers
  with WordSpecLike
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val LRC = "LRC"
  val WETH = "WETH"
  val ETH = "ETH"

  val LRC_TOKEN = XTokenMetadata(LRC, 0, 0.1, 1.0)
  val WETH_TOKEN = XTokenMetadata(WETH, 18, 0.4, 1000)
  val ETH_TOKEN = XTokenMetadata(WETH, 18, 0.4, 1000)

  implicit val marketId = XMarketId(LRC, WETH)
  implicit val tokenMetadataManager = new TokenMetadataManager()
  tokenMetadataManager.addToken(LRC_TOKEN)
  tokenMetadataManager.addToken(WETH_TOKEN)
  tokenMetadataManager.addToken(ETH_TOKEN)
  implicit val tokenValueEstimator = new TokenValueEstimator()
  implicit val dustOrderEvaluator = new DustOrderEvaluator()
  implicit val ringIncomeEstimator = new RingIncomeEstimatorImpl()
  implicit val timeProvider = new SystemTimeProvider()
  implicit val timeout = Timeout(5 second)
  implicit val ec = system.dispatcher

  val config = XMarketManagerConfig()
  val orderbookConfig = XOrderbookConfig(
    levels = 2,
    priceDecimals = 5,
    precisionForAmount = 2,
    precisionForTotal = 1
  )
  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()
  val aggregator = new OrderAwareOrderbookAggregatorImpl(config.priceDecimals)

  val accountBalanceProbe = TestProbe("accountBalance")
  val accountBalanceActor = accountBalanceProbe.ref

  val settlementProbe = TestProbe("settlement")
  val settlementActor = settlementProbe.ref
  //  {
  //   def expectUpdate(balanceMap: Map[String, XBalanceAndAllowance]) = {
  //     expectMsgPF() {
  //       case req: XGetBalanceAndAllowancesReq ⇒
  //         // val ba = req.tokens map {
  //         //   token ⇒
  //         //     token → XBalanceAndAllowance(
  //         //       BigInt("10000000000000000"),
  //         //       BigInt("10000000000000000")
  //         //     )
  //         // }
  //         sender ! XGetBalanceAndAllowancesRes(req.address, balanceMap)
  //     }

  //   }
  // }

  val gasPriceActor = TestActorRef(new GasPriceActor)
  val orderbookManagerActor = TestActorRef(new OrderbookManagerActor(orderbookConfig))

  val address1 = "address_1"
  val address2 = "address_2"

  val accountManagerActor1: ActorRef = TestActorRef(
    new AccountManagerActor(address1)
  )

  val accountManagerActor2: ActorRef = TestActorRef(
    new AccountManagerActor(address2)
  )

  val marketManagerActor: ActorRef = TestActorRef(
    new MarketManagerActor(marketId, config)
  )

  accountManagerActor1 ! ActorDependencyReady(Seq(
    accountBalanceActor.path.toString,
    marketManagerActor.path.toString
  ))

  accountManagerActor2 ! ActorDependencyReady(Seq(
    accountBalanceActor.path.toString,
    marketManagerActor.path.toString
  ))

  marketManagerActor ! ActorDependencyReady(Seq(
    gasPriceActor.path.toString,
    orderbookManagerActor.path.toString,
    settlementActor.path.toString
  ))
}
