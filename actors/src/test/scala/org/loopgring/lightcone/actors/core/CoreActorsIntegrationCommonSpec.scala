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
import org.slf4s.Logging

abstract class CoreActorsIntegrationCommonSpec
  extends TestKit(ActorSystem("test", ConfigFactory.parseString(
    """akka {
         loglevel = "DEBUG"
         actor {
           debug {
             receive = on
             lifecycle = off
           }
         }
       }"""
  ).withFallback(ConfigFactory.load())))
  with ImplicitSender
  with Matchers
  with WordSpecLike
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with Logging {

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

  // Simulating an AccountBalanceActor
  val accountBalanceProbe = new TestProbe(system, "account_balance") {
    def expectQuery(address: String, token: String) = expectMsgPF() {
      case XGetBalanceAndAllowancesReq(addr, tokens) if addr == address && tokens == Seq(token) ⇒
    }

    def replyWith(token: String, balance: BigInt, allowance: BigInt) = reply(
      XGetBalanceAndAllowancesRes(
        ADDRESS_1, Map(token -> XBalanceAndAllowance(balance, allowance))
      )
    )
  }
  val accountBalanceActor = accountBalanceProbe.ref

  // Simulating an OrderHistoryProbe
  val orderHistoryProbe = new TestProbe(system, "order_history") {
    def expectQuery(orderId: String) = expectMsgPF() {
      case XGetOrderFilledAmountReq(id) if id == orderId ⇒
    }

    def replyWith(orderId: String, filledAmountS: BigInt) = reply(
      XGetOrderFilledAmountRes(orderId, filledAmountS)
    )
  }
  val orderHistoryActor = orderHistoryProbe.ref

  // Simulating an SettlementActor
  val ethereumProbe = new TestProbe(system, "ethereum")
  val ethereumActor = ethereumProbe.ref

  val settlementActor = TestActorRef(new SettlementActor("0xa1"))

  val gasPriceActor = TestActorRef(new GasPriceActor)
  val orderbookManagerActor = TestActorRef(new OrderbookManagerActor(orderbookConfig))

  val ADDRESS_1 = "address_111111111111111111111"
  val ADDRESS_2 = "address_222222222222222222222"

  val accountManagerActor1: ActorRef = TestActorRef(
    new AccountManagerActor(ADDRESS_1)
  )

  val accountManagerActor2: ActorRef = TestActorRef(
    new AccountManagerActor(ADDRESS_2)
  )

  val marketManagerActor: ActorRef = TestActorRef(
    new MarketManagerActor(marketId, config)
  )

  accountManagerActor1 ! ActorDependencyReady(Seq(
    accountBalanceActor.path.toString,
    orderHistoryActor.path.toString,
    marketManagerActor.path.toString
  ))

  accountManagerActor2 ! ActorDependencyReady(Seq(
    accountBalanceActor.path.toString,
    orderHistoryActor.path.toString,
    marketManagerActor.path.toString
  ))

  marketManagerActor ! ActorDependencyReady(Seq(
    gasPriceActor.path.toString,
    orderbookManagerActor.path.toString,
    settlementActor.path.toString
  ))

  settlementActor ! ActorDependencyReady(Seq(
    gasPriceActor.path.toString,
    ethereumActor.path.toString
  ))
}
