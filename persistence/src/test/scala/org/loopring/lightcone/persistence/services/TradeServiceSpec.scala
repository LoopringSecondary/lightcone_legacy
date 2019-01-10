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

import org.loopring.lightcone.lib._
import org.loopring.lightcone.persistence.dals.TradeDalImpl
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto._
import scala.concurrent._
import scala.concurrent.duration._

class TradeServiceSpec extends ServiceSpec[TradeService] {
  def getService = new TradeServiceImpl()
  val tokenS = "0xaaaaaa1"
  val tokenB = "0xbbbbbb1"

  def createTables() {
    new TradeDalImpl().createTable()
  }

  private def testSave(
      txHash: String,
      owner: String,
      tokenS: String,
      tokenB: String,
      blockHeight: Long
    ): Future[Either[ErrorCode, String]] = {
    service.saveTrade(
      Trade(
        txHash = txHash,
        owner = owner,
        tokenB = tokenB,
        tokenS = tokenS,
        blockHeight = blockHeight
      )
    )
  }

  "saveTrade" must "save a trade with hash" in {
    val hash = "0x-savetrade-01"
    val result = for {
      _ <- testSave(hash, hash, tokenS, tokenB, 1L)
      _ <- testSave("0x-mock-01", "0x-mock-01", tokenS, tokenB, 1L)
      _ <- testSave("0x-mock-02", "0x-mock-02", tokenS, tokenB, 1L)
      query <- service.getTrades(
        GetTrades.Req(
          owner = hash,
          market = GetTrades.Req.Market
            .Pair(MarketPair(tokenB = tokenB, tokenS = tokenS))
        )
      )
    } yield query
    val res = Await.result(result.mapTo[Seq[Trade]], 5.second)
    res.length == 1 should be(true)
  }

  "getTrades" must "get some trades with many query parameters" in {
    val hashes = Set(
      "0x-gettrades-state0-01",
      "0x-gettrades-state0-02",
      "0x-gettrades-state0-03",
      "0x-gettrades-state0-04",
      "0x-gettrades-state0-05"
    )
    val mockToken = Set(
      "0x-gettrades-token-01",
      "0x-gettrades-token-02",
      "0x-gettrades-token-03",
      "0x-gettrades-token-04"
    )
    val tokenS = "0xaaaaaaa2"
    val tokenB = "0xbbbbbbb2"
    val result = for {
      _ <- Future.sequence(hashes.map { hash =>
        testSave(hash, hash, tokenS, tokenB, 1L)
      })
      _ <- Future.sequence(mockToken.map { hash =>
        testSave(hash, hash, "0x00001", "0x00002", 1L)
      })
      query1 <- service.getTrades(
        GetTrades.Req(
          owner = "0x-gettrades-state0-02",
          market = GetTrades.Req.Market
            .MarketHash(MarketHashProvider.convert2Hex(tokenS, tokenB))
        )
      )
      query2 <- service.getTrades(
        GetTrades.Req(
          owner = "0x-gettrades-token-02",
          market = GetTrades.Req.Market
            .Pair(MarketPair(tokenB = "0x00002", tokenS = "0x00001"))
        )
      )
    } yield (query1, query2)
    val res = Await.result(result.mapTo[(Seq[Trade], Seq[Trade])], 5.second)
    val x = res._1.length === 1 && res._2.length === 1
    x should be(true)
  }

  "countTrades" must "get trades count with many query parameters" in {
    val owners = Set(
      "0x-counttrades-01",
      "0x-counttrades-02",
      "0x-counttrades-03",
      "0x-counttrades-04",
      "0x-counttrades-05",
      "0x-counttrades-06"
    )
    val result = for {
      _ <- Future.sequence(owners.map { hash =>
        testSave(hash, hash, tokenS, tokenB, 1L)
      })
      query <- service.countTrades(
        GetTrades.Req(
          owner = "0x-counttrades-02",
          market = GetTrades.Req.Market
            .MarketHash(MarketHashProvider.convert2Hex(tokenS, tokenB))
        )
      )
    } yield query
    val res = Await.result(result.mapTo[Int], 5.second)
    res should be(1)
  }

  "obsolete" must "obsolete some trades" in {
    val txHashes1 = Set(
      "0x-obsolete-01",
      "0x-obsolete-02",
      "0x-obsolete-03",
      "0x-obsolete-04",
      "0x-obsolete-05",
      "0x-obsolete-06",
      "0x-obsolete-07",
      "0x-obsolete-08",
      "0x-obsolete-09",
      "0x-obsolete-10",
      "0x-obsolete-11",
      "0x-obsolete-12"
    )
    val txHashes2 = Set(
      "0x-obsolete-101",
      "0x-obsolete-102",
      "0x-obsolete-103",
      "0x-obsolete-104",
      "0x-obsolete-105",
      "0x-obsolete-106",
      "0x-obsolete-107",
      "0x-obsolete-108",
      "0x-obsolete-109",
      "0x-obsolete-110",
      "0x-obsolete-111",
      "0x-obsolete-112"
    )
    val owner = "0x-fixed-owner-01"
    val result = for {
      _ <- Future.sequence(txHashes1.map { hash =>
        testSave(hash, owner, tokenS, tokenB, 100L)
      })
      _ <- Future.sequence(txHashes2.map { hash =>
        testSave(hash, owner, tokenS, tokenB, 20L)
      })
      count1 <- service.countTrades(
        GetTrades.Req(
          owner = owner,
          market = GetTrades.Req.Market
            .MarketHash(MarketHashProvider.convert2Hex(tokenS, tokenB))
        )
      )
      _ <- service.obsolete(30L)
      count2 <- service.countTrades(
        GetTrades.Req(
          owner = owner,
          market = GetTrades.Req.Market
            .MarketHash(MarketHashProvider.convert2Hex(tokenS, tokenB))
        )
      )
    } yield (count1, count2)
    val res = Await.result(result.mapTo[(Int, Int)], 5.second)
    val x = res._1 == 24 && res._2 == 12
    x should be(true)
  }
}
