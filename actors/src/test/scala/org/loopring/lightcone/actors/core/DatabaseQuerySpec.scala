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
import org.loopring.lightcone.core.base.MarketKey
import org.loopring.lightcone.proto._

import scala.concurrent.{Await, Future}

class DatabaseQuerySpec
    extends CommonSpec
    with DatabaseModuleSupport
    with DatabaseQueryMessageSupport
    with JsonrpcSupport
    with OrderGenerateSupport
    with HttpSupport {

  private def testSaveTrade(
      txHash: String,
      owner: String,
      tokenS: String,
      tokenB: String,
      blockHeight: Long
    ): Future[Either[ErrorCode, String]] = {
    dbModule.tradeService.saveTrade(
      Trade(
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
      val rawOrders = (0 until 6) map { i =>
        createRawOrder(
          amountS = "10".zeros(LRC_TOKEN.decimals),
          amountFee = (i + 4).toString.zeros(LRC_TOKEN.decimals)
        )
      }
      val request = GetOrdersForUser.Req(
        owner = accounts(0).getAddress,
        statuses = Seq(OrderStatus.STATUS_NEW),
        market = GetOrdersForUser.Req.Market
          .Pair(
            MarketPair(tokenS = LRC_TOKEN.address, tokenB = WETH_TOKEN.address)
          )
      )
      val r = for {
        _ <- Future.sequence(rawOrders.map { order =>
          dbModule.orderService.saveOrder(order)
        })
        response <- singleRequest(request, "get_orders")
      } yield response
      val res = Await.result(r, timeout.duration)
      res match {
        case GetOrdersForUser.Res(orders, error) =>
          assert(orders.nonEmpty && orders.length === 6)
          assert(error === ErrorCode.ERR_NONE)
        case _ => assert(false)
      }
    }
  }

  "send an trades request" must {
    "receive a response without trades" in {
      val method = "get_trades"
      val tokenS = "0xaaaaaaa2"
      val tokenB = "0xbbbbbbb2"
      val owner = "0xa112dae0a3e4e146bcaf0fe782be5afb14041a10"
      val tradesReq = GetTrades.Req(
        owner = owner,
        market = GetTrades.Req.Market
          .MarketHash(MarketKey(tokenS, tokenB).toString),
        skip = Some(Paging(0, 10)),
        sort = SortingType.ASC
      )
      val hashes = Set(
        "0x-gettrades-actor-01",
        "0x-gettrades-actor-02",
        "0x-gettrades-actor-03",
        "0x-gettrades-actor-04",
        "0x-gettrades-actor-05"
      )
      val r = for {
        _ <- Future.sequence(hashes.map { hash =>
          testSaveTrade(hash, owner, tokenS, tokenB, 1L)
        })
        response <- singleRequest(tradesReq, method)
      } yield response
      val res = Await.result(r, timeout.duration)
      res match {
        case GetTrades.Res(trades, error) =>
          assert(trades.nonEmpty && trades.length === 5)
          assert(error === ErrorCode.ERR_NONE)
        case _ => assert(false)
      }
    }
  }
}
