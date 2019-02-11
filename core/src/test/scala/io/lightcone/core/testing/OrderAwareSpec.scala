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

package io.lightcone.core.testing
import com.typesafe.config.ConfigFactory
import io.lightcone.core._

trait OrderAwareSpec extends CommonSpec with OrderHelper {
  var nextId = 1

  val configStr =
    """
      |loopring_protocol {
      |  burn-rate-table {
      |    base = 1000,
      |    tiers = [
      |      {
      |        tier = 3,
      |        rates {
      |          market:50,
      |          p2p:5
      |        }
      |      },
      |      {
      |        tier = 2,
      |        rates {
      |          market:200,
      |          p2p:20
      |        }
      |      },
      |      {
      |        tier = 1,
      |        rates {
      |          market:400,
      |          p2p:30
      |        }
      |      },
      |      {
      |        tier = 0,
      |        rates {
      |          market:600,
      |          p2p:60
      |        }
      |      },
      |    ]
      |  }
      |}
    """.stripMargin

  implicit val config = ConfigFactory.parseString(configStr)

  val tokens = Seq(
    TokenMetadata(
      address = LRC,
      decimals = 0,
      burnRateForMarket = 0.2,
      burnRateForP2P = 0.2,
      symbol = "LRC",
      usdPrice = 1.0
    ),
    TokenMetadata(
      address = GTO,
      decimals = 10,
      burnRateForMarket = 0.2,
      burnRateForP2P = 0.2,
      symbol = "GTO",
      usdPrice = 1400.0
    ),
    TokenMetadata(
      address = DAI,
      decimals = 20,
      burnRateForMarket = 0.3,
      burnRateForP2P = 0.3,
      symbol = "DAI",
      usdPrice = 7.0
    ),
    TokenMetadata(
      address = WETH,
      decimals = 23,
      burnRateForMarket = 0.3,
      burnRateForP2P = 0.3,
      symbol = "WETH",
      usdPrice = 0.5
    )
  )
  implicit val tm = new MetadataManager()
  tm.reset(tokens, Seq.empty)

  implicit val tve = new TokenValueEvaluator

  implicit val dustEvaluator = new DustOrderEvaluator

  implicit var orderPool: AccountOrderPool with UpdatedOrdersTracing = _
  var accountManager: AccountManager = _
  var lrc: ReserveManager = _
  var gto: ReserveManager = _
  var dai: ReserveManager = _
  var weth: ReserveManager = _

  var updatedOrders = Map.empty[String, Matchable]

  override def beforeEach(): Unit = {
    nextId = 1
    orderPool = new AccountOrderPoolImpl() with UpdatedOrdersTracing
    updatedOrders = Map.empty[String, Matchable]
    orderPool.addCallback { order =>
      updatedOrders += order.id -> order
    // println("----UO: " + order)
    // log.debug("order: " + order)
    }
    accountManager = AccountManager.default()

    lrc = new ReserveManagerImpl(LRC)
    gto = new ReserveManagerImpl(GTO)
    dai = new ReserveManagerImpl(DAI)
    weth = new ReserveManagerImpl(WETH)

    accountManager.addReserveManager(lrc)
    accountManager.addReserveManager(gto)
    accountManager.addReserveManager(dai)
    accountManager.addReserveManager(weth)
  }

  def sellLRC(
      amountLRC: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(LRC, WETH, LRC, amountLRC, amountWETH, amountFee)

  def buyLRC(
      amountLRC: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, LRC, LRC, amountWETH, amountLRC, amountFee)

  def sellDAI(
      amountDAI: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(DAI, WETH, LRC, amountDAI, amountWETH, amountFee)

  def buyDAI(
      amountDAI: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, DAI, LRC, amountWETH, amountDAI, amountFee)

  def sellGTO(
      amountGTO: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(GTO, WETH, LRC, amountGTO, amountWETH, amountFee)

  def buyGTO(
      amountGTO: BigInt,
      amountWETH: BigInt,
      amountFee: BigInt
    ) = newOrder(WETH, GTO, LRC, amountWETH, amountGTO, amountFee)

  def newOrder(
      tokenS: String,
      tokenB: String,
      tokenFee: String,
      amountS: BigInt,
      amountB: BigInt,
      amountFee: BigInt
    ): Matchable =
    Matchable(
      getNextId(),
      tokenS,
      tokenB,
      tokenFee,
      amountS,
      amountB,
      amountFee
    )

  def sellLRC(
      amountLRC: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(LRC, WETH, LRC, amountLRC, amountWETH, amountFee)

  def buyLRC(
      amountLRC: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, LRC, LRC, amountWETH, amountLRC, amountFee)

  def sellDAI(
      amountDAI: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(DAI, WETH, LRC, amountDAI, amountWETH, amountFee)

  def buyDAI(
      amountDAI: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, DAI, LRC, amountWETH, amountDAI, amountFee)

  def sellGTO(
      amountGTO: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(GTO, WETH, LRC, amountGTO, amountWETH, amountFee)

  def buyGTO(
      amountGTO: Double,
      amountWETH: Double,
      amountFee: Double = 0
    ) = newOrder(WETH, GTO, LRC, amountWETH, amountGTO, amountFee)

  def newOrder(
      tokenS: String,
      tokenB: String,
      tokenFee: String,
      amountS: Double,
      amountB: Double,
      amountFee: Double
    ): Matchable =
    Matchable(
      getNextId(),
      tokenS,
      tokenB,
      tokenFee,
      amountS.toWei(tokenS),
      amountB.toWei(tokenB),
      amountFee.toWei(tokenFee)
    )

  def orderState(
      amountS: BigInt,
      amountB: BigInt,
      amountFee: BigInt
    ) = MatchableState(amountS, amountB, amountFee)

  def submitOrder(order: Matchable) = {
    updatedOrders = Map.empty[String, Matchable]
    accountManager.submitOrder(order)
  }

  def cancelOrder(orderId: String) = {
    updatedOrders = Map.empty[String, Matchable]
    accountManager.cancelOrder(orderId)
  }

  def adjustOrder(
      orderId: String,
      outstandingAmountS: BigInt
    ) = {
    updatedOrders = Map.empty[String, Matchable]
    accountManager.adjustOrder(orderId, outstandingAmountS)
  }

  def resetUpdatedOrders(): Unit = {
    updatedOrders = Map.empty[String, Matchable]
  }

  def getNextId() = {
    val id = nextId
    nextId += 1
    id.toString
  }

}
