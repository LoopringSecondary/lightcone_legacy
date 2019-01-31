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

package org.loopring.lightcone.actors.entrypoint

import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.actors.core.RingAndTradePersistenceActor
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.proto.Trade.Fee
import org.loopring.lightcone.proto._
import scala.concurrent.Await
import scala.concurrent._
import scala.concurrent.duration._

class EntryPointSpec_DatabaseQuery
    extends CommonSpec
    with DatabaseModuleSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with DatabaseQueryMessageSupport
    with EventPersistenceSupport {

  val ringAndTradePersistActor = actors.get(RingAndTradePersistenceActor.name)

  "DatabaseQueryActor Entrypoint" must {
    "get trades" in {
      info("save some trades")
      testSaveSomeTrades()
      Thread.sleep(2000)

      info("query trades: by owner")
      val q3 = GetTrades.Req(owner = owner1)
      val r3 = Await.result(
        singleRequest(q3, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r3.trades.length == 2 && r3.total == 2)

      info("query trades: sort")
      val q3_2 = GetTrades.Req(owner = owner1, sort = SortingType.DESC)
      val r3_2 = Await.result(
        singleRequest(q3_2, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r3_2.trades.length == 2 && r3_2.total == 2)
      assert(r3.trades.head == r3_2.trades.last)

      info("query trades: skip")
      val q3_3 = GetTrades.Req(skip = Some(Paging(skip = 1, size = 10)))
      val r3_3 = Await.result(
        singleRequest(q3_3, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r3_3.trades.length == 3 && r3_3.total == 3)

      info("query trades: by owner and market")
      val q4 = GetTrades.Req(
        owner = owner1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1))
      )
      val r4 = Await.result(
        singleRequest(q4, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r4.trades.length == 1 && r4.total == 1)
      val q5 =
        GetTrades.Req(
          owner = owner1,
          market = Some(GetTrades.Req.Market(tokenS1, tokenB1, true))
        )
      val r5 = Await.result(
        singleRequest(q5, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r5.trades.length == 2 && r5.total == 2)

      info("query trades: by ring")
      val q6 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2)))
      val r6 = Await.result(
        singleRequest(q6, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r6.trades.length == 2 && r6.total == 2)
      val q7 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "2", "1")))
      val r7 = Await.result(
        singleRequest(q7, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r7.trades.length == 1 && r7.total == 1)
      val q8 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "2", "2")))
      val r8 = Await.result(
        singleRequest(q8, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r8.trades.isEmpty && r8.total == 0)

      info("query trades: full parameters")
      val q9 = GetTrades.Req(
        owner = owner1,
        txHash = hash1,
        orderHash = hash1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1)),
        ring = Some(GetTrades.Req.Ring(hash1, "1", "0")),
        wallet = wallet,
        miner = miner
      )
      val r9 = Await.result(
        singleRequest(q9, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r9.trades.length == 1 && r9.total == 1)
      val q10 = GetTrades.Req(
        owner = owner2,
        txHash = hash1,
        orderHash = hash1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1)),
        ring = Some(GetTrades.Req.Ring(hash1, "1", "0")),
        wallet = wallet,
        miner = miner
      )
      val r10 = Await.result(
        singleRequest(q10, "get_trades").mapTo[GetTrades.Res],
        5.second
      )
      assert(r10.trades.isEmpty && r10.total == 0)

      info("invalid ringIndex")
      val q11 =
        GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "invalidIndex")))
      try {
        Await.result(
          singleRequest(q11, "get_trades").mapTo[GetTrades.Res],
          5.second
        )
        assert(false)
      } catch {
        case e: Throwable if e.getMessage.indexOf("invalid ringIndex") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }

      info("invalid fillIndex")
      val q12 =
        GetTrades.Req(
          ring = Some(GetTrades.Req.Ring(hash2, "2", "invalidIndex"))
        )
      try {
        Await.result(
          singleRequest(q12, "get_trades").mapTo[GetTrades.Res],
          5.second
        )
        assert(false)
      } catch {
        case e: Throwable if e.getMessage.indexOf("invalid fillIndex") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }
    }

    "get rings" in {
      info("save some rings")
      testSaveSomeRings()
      Thread.sleep(2000)

      info("query rings: by ringHash and ringIndex")
      val q3 = GetRings.Req(
        ring = Some(GetRings.Req.Ring(GetRings.Req.Ring.Filter.RingHash(hash2)))
      )
      val r3 = Await.result(
        singleRequest(q3, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(r3.rings.length == 1 && r3.total == 1)
      val q4 = GetRings.Req(
        ring = Some(GetRings.Req.Ring(GetRings.Req.Ring.Filter.RingIndex(11)))
      )
      val r4 = Await.result(
        singleRequest(q4, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(r4.rings.length == 1 && r4.total == 1)
      assert(r3.rings.head == r4.rings.head)
      r3.rings.head.fees match {
        case Some(f) =>
          assert(f.fees.length == 2 && f.fees == fees.fees)
        case None => assert(false)
      }

      info("query rings: sort")
      val q5 = GetRings.Req(sort = SortingType.DESC)
      val r5 = Await.result(
        singleRequest(q5, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(r5.rings.length == 3 && r5.total == 3)
      val q6 = GetRings.Req(sort = SortingType.ASC)
      val r6 = Await.result(
        singleRequest(q6, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(r6.rings.length == 3 && r6.total == 3)
      assert(r5.rings.head == r6.rings.last)

      info("query rings: skip")
      val q7 = GetRings.Req(skip = Some(Paging(skip = 1, size = 10)))
      val r7 = Await.result(
        singleRequest(q7, "get_rings").mapTo[GetRings.Res],
        5.second
      )
      assert(r7.rings.length == 2 && r7.total == 2)
    }
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
    ringAndTradePersistActor ! PersistTrades.Req(trades)
    // dbModule.tradeService.saveTrades(trades)
  }

  val hash3 =
    "0x30f3c30128432ef6b0bbf3d89002a6af96768f74390ff3061a4f548848e669dc"

  val fees = Ring.Fees(
    Seq(
      Trade.Fee(
        tokenFee = "0x97241525fe425C90eBe5A41127816dcFA5954b06",
        amountFee = ByteString.copyFrom("10", "UTF-8"),
        feeAmountS = ByteString.copyFrom("11", "UTF-8"),
        feeAmountB = ByteString.copyFrom("12", "UTF-8"),
        feeRecipient = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc",
        waiveFeePercentage = 10,
        walletSplitPercentage = 5
      ),
      Trade.Fee(
        tokenFee = "0x2d92e8a4556e9100f1bd7709293f122f69d2cd2b",
        amountFee = ByteString.copyFrom("20", "UTF-8"),
        feeAmountS = ByteString.copyFrom("21", "UTF-8"),
        feeAmountB = ByteString.copyFrom("22", "UTF-8"),
        feeRecipient = "0xa1c95e17f629d8bc5985f3f997760a575d56b0c2",
        waiveFeePercentage = 8,
        walletSplitPercentage = 2
      )
    )
  )

  private def testSaveSomeRings() = {
    val rings = Seq(
      Ring(
        ringHash = hash1,
        ringIndex = 10,
        fillsAmount = 2,
        miner = miner,
        txHash = hash1,
        fees = Some(fees),
        blockHeight = 100
      ),
      Ring(
        ringHash = hash2,
        ringIndex = 11,
        fillsAmount = 2,
        miner = miner,
        txHash = hash2,
        fees = Some(fees),
        blockHeight = 110
      ),
      Ring(
        ringHash = hash3,
        ringIndex = 12,
        fillsAmount = 2,
        miner = miner,
        txHash = hash3,
        fees = Some(fees),
        blockHeight = 120
      )
    )
    ringAndTradePersistActor ! PersistRings.Req(rings)
    // dbModule.ringService.saveRings(rings)
  }

}
