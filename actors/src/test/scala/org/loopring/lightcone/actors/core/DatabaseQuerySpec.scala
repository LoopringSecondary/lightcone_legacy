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

import org.loopring.lightcone.actors.support._
import scala.concurrent.{Await, Future}
import scala.concurrent.Future
import com.google.protobuf.ByteString
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.proto._

class DatabaseQuerySpec
    extends CommonSpec("""
                         |akka.cluster.roles=["database_query"]
                         |""".stripMargin)
    with DatabaseModuleSupport
    with DatabaseQueryMessageSupport
    with JsonrpcSupport
    with HttpSupport {
  val tokenS = "0xaaaaaa1"
  val tokenB = "0xbbbbbb1"
  val tokenFee = "0x-fee-token"
  val validSince = 1
  val validUntil = timeProvider.getTimeSeconds()

  private def testSaveOrder(
      hash: String,
      owner: String,
      status: XOrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int,
      validUntil: Int
    ): Future[Either[XRawOrder, XErrorCode]] = {
    val now = timeProvider.getTimeMillis
    val state =
      XRawOrder.State(createdAt = now, updatedAt = now, status = status)
    val fee = XRawOrder.FeeParams(
      tokenFee = tokenFee,
      amountFee = ByteString.copyFrom("111", "utf-8")
    )
    val param = XRawOrder.Params(validUntil = validUntil)
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
      marketHash = MarketHashProvider.convert2Hex(tokenS, tokenB)
    )
    dbModule.orderService.saveOrder(order)
  }

  private def testSaveOrders(
      hashes: Set[String],
      status: XOrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int,
      validUntil: Int
    ): Future[Set[Either[XRawOrder, XErrorCode]]] = {
    for {
      result ← Future.sequence(hashes.map { hash ⇒
        testSaveOrder(
          hash,
          hash,
          status,
          tokenS,
          tokenB,
          validSince,
          validUntil
        )
      })
    } yield result
  }

  private def testSaveTrade(
      txHash: String,
      owner: String,
      tokenS: String,
      tokenB: String,
      blockHeight: Long
    ): Future[Either[XErrorCode, String]] = {
    dbModule.tradeService.saveTrade(
      XTrade(
        txHash = txHash,
        owner = owner,
        tokenB = tokenB,
        tokenS = tokenS,
        blockHeight = blockHeight
      )
    )
  }

  "send an orders request" must {
    "receive a response without orders" in {
      val method = "get_orders"
      val hashes = Set(
        "0x-getorders-actor-01",
        "0x-getorders-actor-02",
        "0x-getorders-actor-03",
        "0x-getorders-actor-04",
        "0x-getorders-actor-05"
      )
      val request = GetOrdersForUserReq(
        owner = "0x-getorders-actor-03",
        statuses = Seq(XOrderStatus.STATUS_NEW),
        market = GetOrdersForUserReq.Market
          .Pair(MarketPair(tokenS = tokenS, tokenB = tokenB))
      )
      val r = for {
        _ ← testSaveOrders(
          hashes,
          XOrderStatus.STATUS_NEW,
          tokenS,
          tokenB,
          validSince,
          validUntil.toInt
        )
        response <- singleRequest(request, method)
      } yield response
      val res = Await.result(r, timeout.duration)
      res match {
        case GetOrdersForUserResult(orders, error) =>
          assert(orders.nonEmpty && orders.length === 1)
          assert(error === XErrorCode.ERR_NONE)
        case _ => assert(false)
      }
    }
  }

  "send an trades request" must {
    "receive a response without trades" in {
      val method = "get_trades"
      val tokenS = "0xaaaaaaa2"
      val tokenB = "0xbbbbbbb2"
      val tradesReq = GetTradesReq(
        owner = "0x-gettrades-actor-02",
        market = GetTradesReq.Market
          .MarketHash(MarketHashProvider.convert2Hex(tokenS, tokenB)),
        skip = Some(XSkip(0, 10)),
        sort = XSort.ASC
      )
      val hashes = Set(
        "0x-gettrades-actor-01",
        "0x-gettrades-actor-02",
        "0x-gettrades-actor-03",
        "0x-gettrades-actor-04",
        "0x-gettrades-actor-05"
      )
      val r = for {
        _ ← Future.sequence(hashes.map { hash ⇒
          testSaveTrade(hash, hash, tokenS, tokenB, 1L)
        })
        response <- singleRequest(tradesReq, method)
      } yield response
      val res = Await.result(r, timeout.duration)
      res match {
        case GetTradesResult(trades, error) =>
          assert(trades.nonEmpty && trades.length === 1)
          assert(error === XErrorCode.ERR_NONE)
        case _ => assert(false)
      }
    }
  }
}
