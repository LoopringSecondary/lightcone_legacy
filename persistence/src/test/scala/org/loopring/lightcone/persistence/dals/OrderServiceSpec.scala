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

package org.loopring.lightcone.persistence.dals

import com.google.protobuf.ByteString
import org.loopring.lightcone.lib.{ MurmurHash, SystemTimeProvider }
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto.core.{ XOrderStatus, XRawOrder }
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class OrderServiceSpec extends ServiceSpec[OrderService] {
  def getService = new OrderServiceImpl()
  val timeProvider = new SystemTimeProvider()
  val tokenS = "0x-tokens"
  val tokenB = "0x-tokenb"
  val tokenFee = "0x-fee-token"
  val validSince = 1
  val validUntil = timeProvider.getTimeSeconds()

  def createTables(): Future[Any] = for {
    _ ← new OrderDalImpl().createTable()
    r ← new BlockDalImpl().createTable()
  } yield r

  private def testSave(
    hash: String,
    owner: String,
    status: XOrderStatus,
    tokenS: String,
    tokenB: String,
    validSince: Int,
    validUntil: Int
  ): Future[XSaveOrderResult] = {
    val now = timeProvider.getTimeMillis
    val state = XRawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = status
    )
    val fee = XRawOrder.FeeParams(
      tokenFee = tokenFee,
      amountFee = ByteString.copyFrom("111", "utf-8")
    )
    val param = XRawOrder.Params(
      validUntil = validUntil
    )
    var order = XRawOrder(
      owner = owner,
      hash = hash,
      version = 1,
      tokenS = tokenS,
      tokenB = tokenB,
      amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"),
      validSince = validSince,
      state = Some(state),
      feeParams = Some(fee),
      params = Some(param),
      marketHash = MurmurHash.hash64(tokenS) ^ MurmurHash.hash64(tokenB)
    )
    service.submitOrder(order)
  }

  private def testSaves(
    hashes: Set[String],
    status: XOrderStatus,
    tokenS: String,
    tokenB: String,
    validSince: Int,
    validUntil: Int
  ): Future[Set[XSaveOrderResult]] = {
    for {
      result ← Future.sequence(hashes.map { hash ⇒
        testSave(hash, hash, status, tokenS, tokenB, validSince, validUntil)
      })
    } yield result
  }

  "submitOrder" must "save a order with hash 0x111" in {
    val hash = "0x-saveorder-state0-01"
    val result = for {
      _ ← testSave(hash, hash, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      query ← service.getOrder(hash)
    } yield query
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
    res should not be empty
  }

  "getOrders" must "get some orders with many query parameters" in {
    val hashes = Set(
      "0x-getorders-state0-01",
      "0x-getorders-state0-02",
      "0x-getorders-state0-03",
      "0x-getorders-state0-04",
      "0x-getorders-state0-05"
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
      "0x-getorders-token-04"
    )
    val tokenS = "0x-getorders-tokens"
    val tokenB = "0x-getorders-tokenb"
    val result = for {
      _ ← testSaves(hashes, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      _ ← testSaves(mockState, XOrderStatus.STATUS_PARTIALLY_FILLED, tokenS, tokenB, validSince, validUntil.toInt)
      _ ← testSaves(mockToken, XOrderStatus.STATUS_PARTIALLY_FILLED, "0x-mock-tokens", "0x-mock-tokenb", 200, 300)
      query ← service.getOrders(Set(XOrderStatus.STATUS_NEW), hashes, Set(tokenS), Set(tokenB),
        Set(MurmurHash.hash64(tokenS) ^ MurmurHash.hash64(tokenB)), Set(tokenFee), Some(XSort.ASC), None)
      queryStatus ← service.getOrders(Set(XOrderStatus.STATUS_PARTIALLY_FILLED), Set.empty, Set.empty, Set.empty, Set.empty,
        Set.empty, Some(XSort.ASC), None)
      queryToken ← service.getOrders(Set(XOrderStatus.STATUS_NEW), mockToken, Set("0x-mock-tokens"), Set("0x-mock-tokenb"), Set.empty,
        Set(tokenFee), Some(XSort.ASC), None)
      queryMarket ← service.getOrders(Set(XOrderStatus.STATUS_NEW), hashes, Set.empty, Set.empty,
        Set(MurmurHash.hash64(tokenS) ^ MurmurHash.hash64(tokenB)), Set.empty, Some(XSort.ASC), None)
      count ← service.countOrders(Set.empty, Set.empty, Set.empty, Set.empty, Set.empty)
    } yield (query, queryStatus, queryToken, queryMarket, count)
    val res = Await.result(result.mapTo[(Seq[XRawOrder], Seq[XRawOrder], Seq[XRawOrder], Seq[XRawOrder], Int)], 5.second)
    val x = res._1.length === hashes.size && res._2.length === 0 && res._3.length === 4 && res._4.length === 5 && res._5 >= 13 // 之前的测试方法可能有插入
    x should be(true)
  }

  "getOrder" must "get a order with hash 0x111" in {
    val owner = "0x-getorder-state0-01"
    val result = for {
      _ ← testSave(owner, owner, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      query ← service.getOrder(owner)
    } yield query
    val res = Await.result(result.mapTo[Option[XRawOrder]], 5.second)
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
      _ ← testSaves(owners, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      query ← service.getOrdersForUser(Set(XOrderStatus.STATUS_NEW), owners, Set(tokenS), Set(tokenB),
        Set(MurmurHash.hash64(tokenB) ^ MurmurHash.hash64(tokenS)), Set(tokenFee), Some(XSort.ASC), None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res.length should be(owners.size)
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
      _ ← testSaves(owners, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      query ← service.countOrders(Set(XOrderStatus.STATUS_NEW), owners, Set(tokenS), Set(tokenB),
        Set(MurmurHash.hash64(tokenB) ^ MurmurHash.hash64(tokenS)))
    } yield query
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be(owners.size)
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
    val result = for {
      _ ← testSaves(owners, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      query ← service.getOrdersForRecover(Set(XOrderStatus.STATUS_NEW), owners, Set(tokenS), Set(tokenB),
        Set(MurmurHash.hash64(tokenB) ^ MurmurHash.hash64(tokenS)), None, Some(XSort.ASC), None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XRawOrder]], 5.second)
    res.length should be(owners.size)
  }

  "updateOrderStatus" must "update order's status with hash 0x111" in {
    val owners = Set(
      "0x-updateorderstatus-01",
      "0x-updateorderstatus-02",
      "0x-updateorderstatus-03",
      "0x-updateorderstatus-04",
      "0x-updateorderstatus-05",
      "0x-updateorderstatus-06"
    )
    val owner = "0x-updateorderstatus-03"
    val result = for {
      _ ← testSaves(owners, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      update ← service.updateOrderStatus(owner, XOrderStatus.STATUS_CANCELLED_BY_USER)
      query ← service.getOrder(owner)
    } yield (update, query)
    val res = Await.result(result.mapTo[(Either[XPersistenceError, String], Option[XRawOrder])], 5.second)
    val x = res._1.isRight && res._2.nonEmpty && res._2.get.state.get.status === XOrderStatus.STATUS_CANCELLED_BY_USER
    x should be(true)
  }

  "updateAmount" must "update order's amount state with hash 0x111" in {
    val owners = Set(
      "0x-updateamount-01",
      "0x-updateamount-02",
      "0x-updateamount-03",
      "0x-updateamount-04",
      "0x-updateamount-05",
      "0x-updateamount-06"
    )
    val hash = "0x-updateamount-03"
    val timeProvider = new SystemTimeProvider()
    val now = timeProvider.getTimeMillis
    val state = XRawOrder.State(
      createdAt = now,
      updatedAt = now,
      status = XOrderStatus.STATUS_PARTIALLY_FILLED,
      actualAmountB = ByteString.copyFrom("111", "UTF-8"),
      actualAmountS = ByteString.copyFrom("112", "UTF-8"),
      actualAmountFee = ByteString.copyFrom("113", "UTF-8"),
      outstandingAmountB = ByteString.copyFrom("114", "UTF-8"),
      outstandingAmountS = ByteString.copyFrom("115", "UTF-8"),
      outstandingAmountFee = ByteString.copyFrom("116", "UTF-8")
    )
    val result = for {
      _ ← testSaves(owners, XOrderStatus.STATUS_NEW, tokenS, tokenB, validSince, validUntil.toInt)
      update ← service.updateAmount(hash, state)
      query ← service.getOrder(hash)
    } yield (update, query)
    val res = Await.result(result.mapTo[(Either[XPersistenceError, String], Option[XRawOrder])], 5.second)
    val x = res._1.isRight && res._2.nonEmpty && res._2.get.state.get.status === XOrderStatus.STATUS_NEW &&
      res._2.get.state.get.actualAmountB === ByteString.copyFrom("111", "UTF-8")
    x should be(true)
  }
}
