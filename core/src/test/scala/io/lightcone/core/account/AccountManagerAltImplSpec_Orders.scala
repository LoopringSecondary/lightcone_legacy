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

// trait AccountManagerAlt {
//   val owner: String

//   def getAccountInfo(token: String): Future[AccountInfo]

//   def setBalanceAndAllowance(
//       token: String,
//       balance: BigInt,
//       allowance: BigInt
//     ): Future[Map[String, Matchable]]

//   def setBalance(
//       token: String,
//       balance: BigInt
//     ): Future[Map[String, Matchable]]

//   def setAllowance(
//       token: String,
//       allowance: BigInt
//     ): Future[Map[String, Matchable]]

//   def resubmitOrder(order: Matchable): Future[(Boolean, Map[String, Matchable])]

//   // soft cancel an order
//   def cancelOrder(orderId: String): Future[(Boolean, Map[String, Matchable])]
//   def cancelOrders(orderIds: Seq[String]): Future[Map[String, Matchable]]
//   def cancelOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
//   def cancelAllOrders(): Future[Map[String, Matchable]]

//   // cancel an order based on onchain cancel event
//   def hardCancelOrder(orderId: String): Future[Map[String, Matchable]]

//   // hard cancel multiple orders
//   def handleCutoff(cutoff: Long): Future[Map[String, Matchable]]

//   def handleCutoff(
//       cutoff: Long,
//       marketHash: String
//     ): Future[Map[String, Matchable]]

//   def purgeOrders(marketPair: MarketPair): Future[Map[String, Matchable]]
// }

/*
case class Matchable(
    id: String,
    tokenS: String,
    tokenB: String,
    tokenFee: String,
    amountS: BigInt = 0,
    amountB: BigInt = 0,
    amountFee: BigInt = 0,
    validSince: Long = -1,
    submittedAt: Long = -1,
    numAttempts: Int = 0,
    status: OrderStatus = OrderStatus.STATUS_NEW,
    walletSplitPercentage: Double = 0,
    _outstanding: Option[MatchableState] = None,
    _reserved: Option[MatchableState] = None,
    _actual: Option[MatchableState] = None,
    _matchable: Option[MatchableState] = None) {
 */
class AccountManagerAltImplSpec_Orders extends AccountManagerAltImplSpec {
  import OrderStatus._

  "AccountManagerAltImpl" should "allow cancelling non-existing orders" in {
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

  it should "submit order with full reserve without fee and cancel it" in {
    stubBalanceAndAllowance(owner, LRC, 1000, 1000)

    val order = (owner |> 100.0.lrc --> 1.0.weth)
      .copy(
        validSince = 123L, // keep as-is
        submittedAt = 456L, // keep as-is
        numAttempts = 12, // keep as-is
        walletSplitPercentage = 0.3, // keep as-is
        _reserved = Some(MatchableState(-1, -1, -1)), // update
        _actual = Some(MatchableState(-2, -2, -2))
      ) // update

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(100, 0, 0)),
      _actual = Some(MatchableState(100, 1, 0))
    )

    // submit the order
    {
      val (success, orderMap) = manager.resubmitOrder(order).await
      success should be(true)
      orderMap.size should be(1)
      orderMap(order.id) should be(expectedOrder)
    }
    // cancel this order
    {
      val (success, orderMap) = manager.cancelOrder(order.id).await
      success should be(true)
      orderMap.size should be(1)
      orderMap(order.id) should be {
        expectedOrder.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
      }
    }
  }

  it should "submit order with partial reserve without fee and cancel it" in {
    stubBalanceAndAllowance(owner, LRC, 2000, 250)

    val order = (owner |> 1000.0.lrc --> 60.0.weth)
      .copy(_outstanding = Some(MatchableState(500, 30, 0))) // will use these values

    info("and use the '_outstanding' field if it is not None ")
    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(250, 0, 0)),
      _actual = Some(MatchableState(250, 15, 0))
    )

    // submit the order
    {
      val (success, orderMap) = manager.resubmitOrder(order).await
      success should be(true)
      orderMap.size should be(1)
      orderMap(order.id) should be(expectedOrder)
    }
    // cancel this order
    {
      val (success, orderMap) = manager.cancelOrder(order.id).await
      success should be(true)
      orderMap.size should be(1)
      orderMap(order.id) should be {
        expectedOrder.copy(status = STATUS_SOFT_CANCELLED_BY_USER)
      }
    }
  }

  it should "submit order with full reserve with fee (same as tokenS) and cancel it" in {
    stubBalanceAndAllowance(owner, LRC, 1000, 1000)

    val order = owner |> 100.0.lrc --> 1.0.weth -- 5.lrc

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(100, 0, 5)),
      _actual = Some(MatchableState(100, 1, 5))
    )

    val (success, orderMap) = manager.resubmitOrder(order).await
    success should be(true)
    orderMap.size should be(1)
    orderMap(order.id) should be(expectedOrder)
  }

  it should "submit order with full reserve with fee (different than tokenS) and cancel it" in {
    stubBalanceAndAllowance(owner, LRC, 1000, 1000)
    stubBalanceAndAllowance(owner, GTO, 10, 10)

    val order = owner |> 100.0.lrc --> 1.0.weth -- 5.gto

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(100, 0, 5)),
      _actual = Some(MatchableState(100, 1, 5))
    )

    val (success, orderMap) = manager.resubmitOrder(order).await
    success should be(true)
    orderMap.size should be(1)
    orderMap(order.id) should be(expectedOrder)
  }

  "a" should "scale order if amountS not enough" in {
    stubBalanceAndAllowance(owner, LRC, 1000, 500)
    stubBalanceAndAllowance(owner, GTO, 20, 20)

    val order = owner |> 1000.0.lrc --> 10.0.weth -- 20.gto

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(500, 0, 20)),
      _actual = Some(MatchableState(500, 5, 10))
    )

    val (success, orderMap) = manager.resubmitOrder(order).await
    success should be(true)
    orderMap.size should be(1)
    orderMap(order.id) should be(expectedOrder)
  }

  it should "scale order if amountFee not enough" in {
    stubBalanceAndAllowance(owner, LRC, 1000, 1000)
    stubBalanceAndAllowance(owner, GTO, 10, 10)

    val order = owner |> 1000.0.lrc --> 10.0.weth -- 20.gto

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(1000, 0, 10)),
      _actual = Some(MatchableState(500, 5, 10))
    )

    val (success, orderMap) = manager.resubmitOrder(order).await
    success should be(true)
    orderMap.size should be(1)
    orderMap(order.id) should be(expectedOrder)
  }

  it should "scale order if amountFee and amountS both insufficient" in {
    stubBalanceAndAllowance(owner, LRC, 400, 1000)
    stubBalanceAndAllowance(owner, GTO, 10, 10)

    val order = owner |> 1000.0.lrc --> 40.0.weth -- 20.gto

    val expectedOrder = order.copy(
      status = STATUS_PENDING,
      _reserved = Some(MatchableState(400, 0, 10)),
      _actual = Some(MatchableState(400, 16, 8))
    )

    val (success, orderMap) = manager.resubmitOrder(order).await
    success should be(true)
    orderMap.size should be(1)
    orderMap(order.id) should be(expectedOrder)
  }

}
