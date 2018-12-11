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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.testkit._
import akka.util.Timeout
import com.typesafe.config._
import org.loopring.lightcone.lib._
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.persistence._
import org.loopring.lightcone.core.base._
import org.loopring.lightcone.core.depth._
import org.loopring.lightcone.core.market._
import org.loopring.lightcone.proto.actors._
import org.loopring.lightcone.proto.persistence._
import org.loopring.lightcone.proto.core._
import org.scalatest._
import org.slf4s.Logging

import scala.concurrent.duration._
import scala.math.BigInt

object CoreActorsIntegrationCommonSpec {

  val GTO = "0x00000000001"
  val WETH = "0x00000000004"
  val LRC = "0x00000000002"

  val GTO_TOKEN = XTokenMetadata(GTO, 10, 0.1, "GTO", 1.0)
  val WETH_TOKEN = XTokenMetadata(WETH, 18, 0.4, "WETH", 1000)
  val LRC_TOKEN = XTokenMetadata(LRC, 18, 0.4, "LRC", 1000)
}

abstract class CoreActorsIntegrationCommonSpec(
    marketId: XMarketId,
    configStr: String
)
  extends TestKit(ActorSystem("test", ConfigFactory.load()))
  with ImplicitSender
  with Matchers
  with WordSpecLike
  with BeforeAndAfterEach
  with BeforeAndAfterAll
  with Logging {

  implicit val config_ = ConfigFactory.parseString(configStr)

  import CoreActorsIntegrationCommonSpec._

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val marketId_ = marketId
  implicit val tokenMetadataManager = new TokenMetadataManager()
  tokenMetadataManager.addToken(GTO_TOKEN)
  tokenMetadataManager.addToken(WETH_TOKEN)
  tokenMetadataManager.addToken(LRC_TOKEN)
  implicit val tokenValueEstimator = new TokenValueEstimator()
  implicit val dustOrderEvaluator = new DustOrderEvaluator()
  implicit val ringIncomeEstimator = new RingIncomeEstimatorImpl()
  implicit val timeProvider = new SystemTimeProvider()
  implicit val timeout = Timeout(5 second)
  implicit val ec = system.dispatcher

  implicit val actors = new MapBasedLookup[ActorRef]()

  val ringMatcher = new RingMatcherImpl()
  val pendingRingPool = new PendingRingPoolImpl()

  // Simulating an AccountBalanceActor
  val ordersDalActorProbe = new TestProbe(system, "order_db_access") {
    def expectQuery() {
      expectMsgPF() {
        case req: XRecoverOrdersReq ⇒
          info(s"ordermanagerProbe receive: $req, sender:${sender()}")
      }
    }
    def replyWith(xorders: Seq[XRawOrder]) = reply(
      XRecoverOrdersRes(orders = xorders)
    )
  }
  val ordersDalActor = ordersDalActorProbe.ref

  // Simulating an AccountBalanceActor
  val accountBalanceProbe = new TestProbe(system, "account_balance") {
    def expectQuery(address: String, token: String) = expectMsgPF() {
      case XGetBalanceAndAllowancesReq(addr, tokens) if addr == address && tokens == Seq(token) ⇒
        info(s"accountBalanceProbe, ${addr}, ${tokens}, ${sender()}")
    }

    def replyWith(addr: String, token: String, balance: BigInt, allowance: BigInt) = reply(
      XGetBalanceAndAllowancesRes(
        addr, Map(token -> XBalanceAndAllowance(balance, allowance))
      )
    )
  }
  val accountBalanceActor = accountBalanceProbe.ref

  // Simulating an OrderHistoryProbe
  val orderHistoryProbe = new TestProbe(system, "order_history") {
    def expectQuery(orderId: String) = expectMsgPF() {
      case XGetOrderFilledAmountReq(id) if id == orderId ⇒
        log.debug(s"orderHistoryProbe, ${sender()}, ${id}")
    }

    def replyWith(orderId: String, filledAmountS: BigInt) = reply(
      XGetOrderFilledAmountRes(orderId, filledAmountS)
    )
  }
  val orderHistoryActor = orderHistoryProbe.ref

  // Simulating an RingSettlementActor
  val ethereumAccessProbe = new TestProbe(system, "ethereum_access")
  val ethereumAccessActor = ethereumAccessProbe.ref

  val settlementActor = TestActorRef(new RingSettlementActor())

  val gasPriceActor = TestActorRef(new GasPriceActor)
  val orderbookManagerActor = TestActorRef(
    new OrderbookManagerActor()
  )

  val ADDRESS_1 = "address_111111111111111111111"
  val ADDRESS_2 = "address_222222222222222222222"

  val accountManagerActor1: ActorRef = TestActorRef(
    new AccountManagerActor()
  )

  val accountManagerActor2: ActorRef = TestActorRef(
    new AccountManagerActor()
  )

  val marketManagerActor: ActorRef = TestActorRef(
    new MarketManagerActor()
  )

  actors.add(OrdersDalActor.name, ordersDalActor)
  actors.add(AccountBalanceActor.name, accountBalanceActor)
  actors.add(OrderHistoryActor.name, orderHistoryActor)
  actors.add(MarketManagerActor.name, marketManagerActor)
  actors.add(GasPriceActor.name, gasPriceActor)
  actors.add(OrderbookManagerActor.name, orderbookManagerActor)
  actors.add(RingSettlementActor.name, settlementActor)
  actors.add(EthereumAccessActor.name, ethereumAccessActor)

  accountManagerActor1 ! XStart(ADDRESS_1)
  accountManagerActor2 ! XStart(ADDRESS_2)
  marketManagerActor ! XStart(marketId.primary + "-" + marketId.secondary)
  settlementActor ! XStart()

  implicit class RichString(s: String) {
    def zeros(size: Int): BigInt = BigInt(s + "0" * size)
  }
}
