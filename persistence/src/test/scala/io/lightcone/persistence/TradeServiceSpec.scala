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

package io.lightcone.persistence

import com.google.protobuf.ByteString
import io.lightcone.lib._
import io.lightcone.core._
import io.lightcone.persistence.dals._
import io.lightcone.relayer.data._
import io.lightcone.relayer.data.GetTrades._
import scala.concurrent._
import scala.concurrent.duration._

class TradeServiceSpec extends ServiceSpec[TradeService] {

  implicit var dal: TradeDal = _

  def getService = {
    dal = new TradeDalImpl()
    new TradeServiceImpl()
  }

  def createTables(): Unit = dal.createTable()

  "tradeService" must "save and query correctly" in {
    info("save some trades")
    val r1 = Await.result(testSaveSomeTrades().mapTo[Seq[ErrorCode]], 5.second)
    assert(r1.length == 4)
    assert(r1.length == 4 && !r1.exists(_ != ErrorCode.ERR_NONE))

    info("save a duplicate trade(txHash and fillIndex) should return error")
    val r2 = Await.result(testDuplicateSave().mapTo[ErrorCode], 5.second)
    assert(r2 == ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT)

    info("query trades: by owner")
    val q3 = Req(owner = owner1)
    val r3 = Await.result(service.getTrades(q3).mapTo[Seq[Trade]], 5.second)
    val c3 = Await.result(service.countTrades(q3).mapTo[Int], 5.second)
    assert(r3.length == 2 && c3 == 2)

    info("query trades: sort")
    val q3_2 = Req(owner = owner1, sort = SortingType.DESC)
    val r3_2 = Await.result(service.getTrades(q3_2).mapTo[Seq[Trade]], 5.second)
    assert(r3_2.length == 2)
    assert(r3.head == r3_2.last)

    info("query trades: skip")
    val q3_3 = Req(skip = Some(Paging(skip = 1, size = 10)))
    val r3_3 = Await.result(service.getTrades(q3_3).mapTo[Seq[Trade]], 5.second)
    assert(r3_3.length == 3)

    info("query trades: by owner and market")
    val q4 = Req(owner = owner1, market = Some(Req.Market(tokenS1, tokenB1)))
    val r4 = Await.result(service.getTrades(q4).mapTo[Seq[Trade]], 5.second)
    val c4 = Await.result(service.countTrades(q4).mapTo[Int], 5.second)
    assert(r4.length == 1 && c4 == 1)
    val q5 =
      Req(owner = owner1, market = Some(Req.Market(tokenS1, tokenB1, true)))
    val r5 = Await.result(service.getTrades(q5).mapTo[Seq[Trade]], 5.second)
    val c5 = Await.result(service.countTrades(q5).mapTo[Int], 5.second)
    assert(r5.length == 2 && c5 == 2)

    info("query trades: by ring")
    val q6 = Req(ring = Some(Req.Ring(hash2)))
    val r6 = Await.result(service.getTrades(q6).mapTo[Seq[Trade]], 5.second)
    val c6 = Await.result(service.countTrades(q6).mapTo[Int], 5.second)
    assert(r6.length == 2 && c6 == 2)
    val q7 = Req(ring = Some(Req.Ring(hash2, "2", "1")))
    val r7 = Await.result(service.getTrades(q7).mapTo[Seq[Trade]], 5.second)
    val c7 = Await.result(service.countTrades(q7).mapTo[Int], 5.second)
    assert(r7.length == 1 && c7 == 1)
    val q8 = Req(ring = Some(Req.Ring(hash2, "2", "2")))
    val r8 = Await.result(service.getTrades(q8).mapTo[Seq[Trade]], 5.second)
    val c8 = Await.result(service.countTrades(q8).mapTo[Int], 5.second)
    assert(r8.isEmpty && c8 == 0)

    info("query trades: full parameters")
    val q9 = Req(
      owner = owner1,
      txHash = hash1,
      orderHash = hash1,
      market = Some(Req.Market(tokenS1, tokenB1)),
      ring = Some(Req.Ring(hash1, "1", "0")),
      wallet = wallet,
      miner = miner
    )
    val r9 = Await.result(service.getTrades(q9).mapTo[Seq[Trade]], 5.second)
    val c9 = Await.result(service.countTrades(q9).mapTo[Int], 5.second)
    assert(r9.length == 1 && c9 == 1)
    val q10 = Req(
      owner = owner2,
      txHash = hash1,
      orderHash = hash1,
      market = Some(Req.Market(tokenS1, tokenB1)),
      ring = Some(Req.Ring(hash1, "1", "0")),
      wallet = wallet,
      miner = miner
    )
    val r10 = Await.result(service.getTrades(q10).mapTo[Seq[Trade]], 5.second)
    val c10 = Await.result(service.countTrades(q10).mapTo[Int], 5.second)
    assert(r10.isEmpty && c10 == 0)

    info("obsolete")
    Await.result(service.obsolete(11L).mapTo[Unit], 5.second)
    val c11 = Await.result(
      service.countTrades(Req(owner = owner1)).mapTo[Int],
      5.second
    )
    val c12 = Await.result(
      service.countTrades(Req(owner = owner2)).mapTo[Int],
      5.second
    )
    assert(c11 == 2 && c12 == 0)
  }

  val owner1 = "0x4385adb3e6b88a6691ae24c8c317b7327d91a8ad"

  val hash1 =
    "0x36ea537d8f02693c7a0c4c0cd590906cfbbe654a96668555e50277b8bec7cc55"
  val tokenS1 = "0x97241525fe425C90eBe5A41127816dcFA5954b06"
  val tokenB1 = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc"
  val owner2 = "0x373d6d769154edbba3049ffa4b40716b276dada8"

  val hash2 =
    "0xba2a688aae3307d96e39c03bfdba97889ed55b4992c8fb121b026273184d7ccc"
  val tokenS2 = "0xa1c95e17f629d8bc5985f3f997760a575d56b0c2"
  val tokenB2 = "0x14b0846eb7fe70cc155138e0da9ab990ffeacc23"
  val miner = "0x624d520bab2e4ad83935fa503fb130614374e850"
  val wallet = "0x74febeff16769960528c7f22acfa8e6df7f9cd53"

  private def testSaveSomeTrades() = {
    val trades = Seq(
      Trade(
        txHash = hash1,
        orderHash = hash1,
        owner = owner1,
        tokenB = tokenB1,
        tokenS = tokenS1,
        amountB = ByteString.copyFrom("1", "UTF-8"),
        amountS = ByteString.copyFrom("10", "UTF-8"),
        blockHeight = 10,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 0,
        miner = miner,
        wallet = wallet
      ),
      Trade(
        txHash = hash1,
        orderHash = hash1,
        owner = owner1,
        tokenB = tokenS1,
        tokenS = tokenB1,
        amountB = ByteString.copyFrom("10", "UTF-8"),
        amountS = ByteString.copyFrom("1", "UTF-8"),
        blockHeight = 10,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 1,
        miner = miner,
        wallet = wallet
      ),
      Trade(
        txHash = hash2,
        orderHash = hash2,
        owner = owner2,
        tokenB = tokenB2,
        tokenS = tokenS2,
        amountB = ByteString.copyFrom("1", "UTF-8"),
        amountS = ByteString.copyFrom("10", "UTF-8"),
        blockHeight = 20,
        ringHash = hash2,
        ringIndex = 2,
        fillIndex = 0,
        miner = miner,
        wallet = wallet
      ),
      Trade(
        txHash = hash2,
        orderHash = hash2,
        owner = owner2,
        tokenB = tokenS2,
        tokenS = tokenB2,
        amountB = ByteString.copyFrom("10", "UTF-8"),
        amountS = ByteString.copyFrom("1", "UTF-8"),
        blockHeight = 20,
        ringHash = hash2,
        ringIndex = 2,
        fillIndex = 1,
        miner = miner,
        wallet = wallet
      )
    )
    service.saveTrades(trades)
  }

  private def testDuplicateSave() = {
    service.saveTrade(
      Trade(
        txHash = hash1,
        owner = owner1,
        tokenB = tokenS1,
        tokenS = tokenB1,
        amountB = ByteString.copyFrom("10", "UTF-8"),
        amountS = ByteString.copyFrom("1", "UTF-8"),
        blockHeight = 1,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 1
      )
    )
  }
}
