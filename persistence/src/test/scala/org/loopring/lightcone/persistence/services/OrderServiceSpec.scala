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

package org.loopring.lightcone.persistence.services

import com.google.protobuf.ByteString
import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.dals._
import org.loopring.lightcone.persistence.service.{
  OrderService,
  OrderServiceImpl
}
import org.loopring.lightcone.proto._
import scala.concurrent._
import scala.concurrent.duration._

class OrderServiceSpec extends ServiceSpec[OrderService] {

  implicit var dal: OrderDal = _

  def getService = {
    dal = new OrderDalImpl()
    new OrderServiceImpl()
  }
  val tokenS = "0xaaaaaa1"
  val tokenB = "0xbbbbbb1"
  val validSince = 1
  val validUntil = timeProvider.getTimeSeconds()

  def createTables() = {
    new OrderDalImpl().createTable()
    new BlockDalImpl().createTable()
  }

  private def testSave(
      owner: String,
      status: OrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int = 0,
      validUntil: Int = 0
    ): Future[Either[RawOrder, ErrorCode]] = {
    val o =
      generateRawOrder(owner, tokenS, tokenB, status, validSince, validUntil)
    service.saveOrder(o)
  }

  private def testSaves(
      owners: Set[String],
      status: OrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int = 0,
      validUntil: Int = 0
    ): Future[Set[Either[RawOrder, ErrorCode]]] = {
    for {
      result <- Future.sequence(owners.map { owner =>
        testSave(owner, status, tokenS, tokenB, validSince, validUntil)
      })
    } yield result
  }

  "marketHash" must "calculate a market hash by two address" in {
    val address1 = "0x50689da538c80f32f46fb224af5d9d06c3309633"
    val address2 = "0x6d0643f40c625a46d4ede0b11031b0907bc197d1"
    MarketHashProvider.convert2BigInt(address1, address2)
    val marketHash1 = MarketHashProvider.convert2BigInt(address1, address2)
    val marketHash2 = MarketHashProvider.convert2BigInt(address2, address1)
    val t = MarketHashProvider.convert2Hex(address1, address2)
    marketHash1.equals(marketHash2) && t === "0x3d6ede5134aa557420825295bf6c2d96b8f101e2" should be(
      true
    )
  }

  "submitOrder" must "save a order with hash" in {
    val owner = "0x-saveorder-state0-01"
    val result = for {
      order <- testSave(owner, OrderStatus.STATUS_NEW, tokenS, tokenB)
      _ = assert(order.isLeft)
      query <- service.getOrder(order.left.get.hash)
    } yield query
    val res = Await.result(result.mapTo[Option[RawOrder]], 5.second)
    res should not be empty
  }

  "getOrders" must "get some orders with many query parameters" in {
    val owners = Set(
      "0x-getorders-state0-01",
      "0x-getorders-state0-02",
      "0x-getorders-state0-03"
    )
    val mockState = Set(
      "0x-getorders-state1-01",
      "0x-getorders-state1-02",
      "0x-getorders-state1-03",
      "0x-getorders-state1-04"
    )
    val mockToken = Set(
      "0x-getorders-token-01",
      "0x-getorders-token-02",
      "0x-getorders-token-03",
      "0x-getorders-token-04",
      "0x-getorders-token-05"
    )
    val tokenS = "0xaaaaaaa2"
    val tokenB = "0xbbbbbbb2"
    val result = for {
      _ <- testSaves(owners, OrderStatus.STATUS_NEW, tokenS, tokenB)
      _ <- testSaves(
        mockState,
        OrderStatus.STATUS_PARTIALLY_FILLED,
        tokenS,
        tokenB
      )
      _ <- testSaves(
        mockToken,
        OrderStatus.STATUS_PARTIALLY_FILLED,
        "0xcccccccc1",
        "0xccccccccc2",
        200,
        300
      )
      query <- service.getOrders(
        Set(OrderStatus.STATUS_NEW),
        owners,
        Set(tokenS),
        Set(tokenB),
        Set(MarketHashProvider.convert2Hex(tokenS, tokenB)),
        Set.empty,
        Some(SortingType.ASC),
        None
      )
      queryStatus <- service.getOrders(
        Set(OrderStatus.STATUS_PARTIALLY_FILLED),
        Set.empty,
        Set.empty,
        Set.empty,
        Set.empty,
        Set.empty,
        Some(SortingType.ASC),
        None
      )
      queryToken <- service.getOrders(
        Set(OrderStatus.STATUS_NEW),
        mockToken,
        Set("0xcccccccc1"),
        Set("0xccccccccc2"),
        Set.empty,
        Set.empty,
        Some(SortingType.ASC),
        None
      )
      queryMarket <- service.getOrders(
        Set(OrderStatus.STATUS_NEW),
        owners,
        Set.empty,
        Set.empty,
        Set(MarketHashProvider.convert2Hex(tokenS, tokenB)),
        Set.empty,
        Some(SortingType.ASC),
        None
      )
      count <- service.countOrdersForUser(Set.empty)
    } yield (query, queryStatus, queryToken, queryMarket, count)
    val res = Await.result(
      result.mapTo[
        (Seq[RawOrder], Seq[RawOrder], Seq[RawOrder], Seq[RawOrder], Int)
      ],
      5.second
    )
    val x = res._1.length === owners.size && res._2.length === 9 && res._3.length === 0 && res._4.length === owners.size && res._5 >= 12 // 之前的测试方法可能有插入
    x should be(true)
  }

  "getOrder" must "get a order with hash" in {
    val owner = "0x-getorder-state0-01"
    val result = for {
      saved <- testSave(owner, OrderStatus.STATUS_NEW, tokenS, tokenB)
      _ = assert(saved.isLeft)
      query <- service.getOrder(saved.left.get.hash)
    } yield query
    val res = Await.result(result.mapTo[Option[RawOrder]], 5.second)
    res should not be empty
  }

  "getOrdersForUser" must "get some orders with many query parameters" in {
    val owners = Set(
      "0x-getordersfouser-01",
      "0x-getordersfouser-02",
      "0x-getordersfouser-03",
      "0x-getordersfouser-04",
      "0x-getordersfouser-05"
    )
    val result = for {
      _ <- testSaves(owners, OrderStatus.STATUS_NEW, tokenS, tokenB)
      q1 <- service.getOrdersForUser(
        Set(OrderStatus.STATUS_NEW),
        Some("0x-getordersfouser-03"),
        Some(tokenS),
        Some(tokenB),
        None,
        None,
        Some(SortingType.ASC),
        None
      )
      q2 <- service.getOrdersForUser(
        Set(OrderStatus.STATUS_NEW),
        Some("0x-getordersfouser-03"),
        None,
        None,
        Some(MarketHashProvider.convert2Hex(tokenS, tokenB)),
        None,
        Some(SortingType.ASC),
        None
      )
    } yield (q1, q2)
    val res =
      Await.result(result.mapTo[(Seq[RawOrder], Seq[RawOrder])], 5.second)
    res._1.length === 1 && res._2.length === 1 should be(true)
  }

  "countOrders" must "get orders count with many query parameters" in {
    val owners = Set(
      "0x-countorders-01",
      "0x-countorders-02",
      "0x-countorders-03",
      "0x-countorders-04",
      "0x-countorders-05",
      "0x-countorders-06"
    )
    val result = for {
      _ <- testSaves(owners, OrderStatus.STATUS_NEW, tokenS, tokenB)
      query <- service.countOrdersForUser(
        Set(OrderStatus.STATUS_NEW),
        Some("0x-countorders-05"),
        Some(tokenS),
        Some(tokenB),
        Some(MarketHashProvider.convert2Hex(tokenS, tokenB))
      )
    } yield query
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be(1)
  }

  "getOrdersForRecover" must "get some orders to recover" in {
    val owners = Set(
      "0x-getordersforrecover-01",
      "0x-getordersforrecover-02",
      "0x-getordersforrecover-03",
      "0x-getordersforrecover-04",
      "0x-getordersforrecover-05",
      "0x-getordersforrecover-06"
    )
    val tokenS = "0xaaaaa01"
    val tokenB = "0xaaaaa02"
    val result = for {
      _ <- testSaves(owners, OrderStatus.STATUS_NEW, tokenS, tokenB)
      marketHash = MarketHashProvider.convert2Hex(tokenS, tokenB)
      marketHashIds = Set(Math.abs(marketHash.hashCode))
      addressShardIds = owners.map(a => Math.abs(a.hashCode % 100)).toSet
      query <- service.getOrdersForRecover(
        Set(OrderStatus.STATUS_NEW),
        marketHashIds,
        addressShardIds,
        CursorPaging(size = 100)
      )
    } yield query
    val res = Await.result(result.mapTo[Seq[RawOrder]], 5.second)
    res.length should be(owners.size)
  }

  "updateOrderStatus" must "update order's status with hash" in {
    val owner = "0x-updateorderstatus-03"
    val result = for {
      saved <- testSave(owner, OrderStatus.STATUS_NEW, tokenS, tokenB)
      _ = assert(saved.isLeft)
      update <- service.updateOrderStatus(
        saved.left.get.hash,
        OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
      )
      query <- service.getOrder(saved.left.get.hash)
    } yield (update, query)
    val res =
      Await.result(result.mapTo[(ErrorCode, Option[RawOrder])], 5.second)
    val x = res._1 === ErrorCode.ERR_NONE && res._2.nonEmpty && res._2.get.state.get.status === OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
    x should be(true)
  }

  "updateAmount" must "update order's amount state with hash" in {
    val owner = "0x-updateamount-03"
    val timeProvider = new SystemTimeProvider()
    val now = timeProvider.getTimeMillis
    val state = RawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = OrderStatus.STATUS_PARTIALLY_FILLED,
      actualAmountB = ByteString.copyFrom("111", "UTF-8"),
      actualAmountS = ByteString.copyFrom("112", "UTF-8"),
      actualAmountFee = ByteString.copyFrom("113", "UTF-8"),
      outstandingAmountB = ByteString.copyFrom("114", "UTF-8"),
      outstandingAmountS = ByteString.copyFrom("115", "UTF-8"),
      outstandingAmountFee = ByteString.copyFrom("116", "UTF-8")
    )
    val result = for {
      saved <- testSave(owner, OrderStatus.STATUS_NEW, tokenS, tokenB)
      _ = assert(saved.isLeft)
      update <- service.updateAmount(saved.left.get.hash, state)
      query <- service.getOrder(saved.left.get.hash)
    } yield (update, query)
    val res =
      Await.result(result.mapTo[(ErrorCode, Option[RawOrder])], 5.second)
    val x = res._1 === ErrorCode.ERR_NONE && res._2.nonEmpty && res._2.get.state.get.status === OrderStatus.STATUS_NEW &&
      res._2.get.state.get.actualAmountB === ByteString.copyFrom("111", "UTF-8")
    x should be(true)
  }

  "markOrderSoftCancelled" must "soft cancel some orders with hash" in {
    val tokenS = "0xbbbbb01"
    val tokenB = "0xbbbbb02"
    val result = for {
      saved1 <- testSave(
        "0x-softcancel-01",
        OrderStatus.STATUS_NEW,
        tokenS,
        tokenB
      )
      _ = assert(saved1.isLeft)
      saved2 <- testSave(
        "0x-softcancel-02",
        OrderStatus.STATUS_NEW,
        tokenS,
        tokenB
      )
      _ = assert(saved2.isLeft)
      saved3 <- testSave(
        "0x-softcancel-03",
        OrderStatus.STATUS_NEW,
        tokenS,
        tokenB
      )
      _ = assert(saved3.isLeft)
      query1 <- service.countOrdersForUser(
        Set(OrderStatus.STATUS_NEW),
        None,
        Some(tokenS),
        Some(tokenB),
        Some(MarketHashProvider.convert2Hex(tokenS, tokenB))
      )
      update <- service.cancelOrders(
        Seq(saved1.left.get.hash, saved3.left.get.hash),
        OrderStatus.STATUS_SOFT_CANCELLED_BY_USER
      )
      query2 <- service.countOrdersForUser(
        Set(OrderStatus.STATUS_NEW),
        None,
        Some(tokenS),
        Some(tokenB),
        Some(MarketHashProvider.convert2Hex(tokenS, tokenB))
      )
      query3 <- service.countOrdersForUser(
        Set(OrderStatus.STATUS_SOFT_CANCELLED_BY_USER),
        None,
        Some(tokenS),
        Some(tokenB),
        Some(MarketHashProvider.convert2Hex(tokenS, tokenB))
      )
    } yield (update, query1, query2, query3)
    val res = Await.result(
      result.mapTo[(Seq[UserCancelOrder.Res.Result], Int, Int, Int)],
      5.second
    )
    res._1.map { o =>
      assert(o.order.isDefined)
    }
    val x = res._1.length === 2 && !res._1.exists(
      _.error !== ErrorCode.ERR_NONE
    ) && res._2 === 3 && res._3 === 1 && res._4 === 2
    x should be(true)
  }
}
