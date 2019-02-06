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

package io.lightcone.relayer.entrypoint

import com.google.protobuf.ByteString
import io.lightcone.relayer.base._
import io.lightcone.relayer.actors.RingAndTradePersistenceActor
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._
import io.lightcone.proto.Trade.Fee
import io.lightcone.proto._
import io.lightcone.persistence._
import io.lightcone.core._
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
  with RingAndTradePersistenceSupport {

  val ringAndTradePersistActor = actors.get(RingAndTradePersistenceActor.name)

  "DatabaseQueryActor Entrypoint" must {
    "init" in {
      info("save some ring and trades")
      testSaveSomeRings()
      Thread.sleep(3000)
    }

    "get trades" in {
      info("query trades: by owner")
      val q3 = GetTrades.Req(owner = owner1)
      val r3 = Await.result(
        singleRequest(q3, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r3.trades.length == 4 && r3.total == 4)

      info("query trades: sort")
      val q3_2 = GetTrades.Req(owner = owner1, sort = SortingType.DESC)
      val r3_2 = Await.result(
        singleRequest(q3_2, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r3_2.trades.length == 4 && r3_2.total == 4)
      assert(r3.trades.head == r3_2.trades.last)

      info("query trades: skip")
      val q3_3 = GetTrades.Req(skip = Some(Paging(skip = 1, size = 10)))
      val r3_3 = Await.result(
        singleRequest(q3_3, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r3_3.trades.length == 5 && r3_3.total == 6)

      info("query trades: by owner and market")
      val q4 = GetTrades.Req(
        owner = owner1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1)))
      val r4 = Await.result(
        singleRequest(q4, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r4.trades.length == 1 && r4.total == 1)
      val q5 =
        GetTrades.Req(
          owner = owner1,
          market = Some(GetTrades.Req.Market(tokenS1, tokenB1, true)))
      val r5 = Await.result(
        singleRequest(q5, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r5.trades.length == 2 && r5.total == 2)

      info("query trades: by ring")
      val q6 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2)))
      val r6 = Await.result(
        singleRequest(q6, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r6.trades.length == 2 && r6.total == 2)
      val q7 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "11", "1")))
      val r7 = Await.result(
        singleRequest(q7, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r7.trades.length == 1 && r7.total == 1)
      val q8 = GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "11", "2")))
      val r8 = Await.result(
        singleRequest(q8, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r8.trades.isEmpty && r8.total == 0)

      info("query trades: full parameters")
      val q9 = GetTrades.Req(
        owner = owner1,
        txHash = hash1,
        orderHash = hash1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1)),
        ring = Some(GetTrades.Req.Ring(hash1, "10", "0")),
        wallet = wallet,
        miner = miner)
      val r9 = Await.result(
        singleRequest(q9, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r9.trades.length == 1 && r9.total == 1)
      val q10 = GetTrades.Req(
        owner = owner2,
        txHash = hash1,
        orderHash = hash1,
        market = Some(GetTrades.Req.Market(tokenS1, tokenB1)),
        ring = Some(GetTrades.Req.Ring(hash1, "1", "0")),
        wallet = wallet,
        miner = miner)
      val r10 = Await.result(
        singleRequest(q10, "get_trades").mapTo[GetTrades.Res],
        5.second)
      assert(r10.trades.isEmpty && r10.total == 0)

      info("invalid ringIndex")
      val q11 =
        GetTrades.Req(ring = Some(GetTrades.Req.Ring(hash2, "invalidIndex")))
      try {
        Await.result(
          singleRequest(q11, "get_trades").mapTo[GetTrades.Res],
          5.second)
        assert(false)
      } catch {
        case e: Throwable if e.getMessage.indexOf("invalid ringIndex") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }

      info("invalid fillIndex")
      val q12 =
        GetTrades.Req(
          ring = Some(GetTrades.Req.Ring(hash2, "2", "invalidIndex")))
      try {
        Await.result(
          singleRequest(q12, "get_trades").mapTo[GetTrades.Res],
          5.second)
        assert(false)
      } catch {
        case e: Throwable if e.getMessage.indexOf("invalid fillIndex") > -1 =>
          assert(true)
        case _: Throwable => assert(false)
      }
    }

    "get rings" in {
      info("query rings: by ringHash and ringIndex")
      val q3 = GetRings.Req(
        ring = Some(GetRings.Req.Ring(GetRings.Req.Ring.Filter.RingHash(hash2))))
      val r3 = Await.result(
        singleRequest(q3, "get_rings").mapTo[GetRings.Res],
        5.second)
      assert(r3.rings.length == 1 && r3.total == 1)
      val q4 = GetRings.Req(
        ring =
          Some(GetRings.Req.Ring(GetRings.Req.Ring.Filter.RingIndex(height2))))
      val r4 = Await.result(
        singleRequest(q4, "get_rings").mapTo[GetRings.Res],
        5.second)
      assert(r4.rings.length == 1 && r4.total == 1)
      assert(r3.rings.head == r4.rings.head)
      r3.rings.head.fees match {
        case Some(f) =>
          val fee = Trade.Fee(
            "0x97241525fe425C90eBe5A41127816dcFA5954b06",
            ByteString.copyFrom("3", "UTF-8"),
            ByteString.copyFrom("5", "UTF-8"),
            ByteString.copyFrom("6", "UTF-8"),
            feeRecipient,
            1,
            1)
          assert(f.fees.length == 2 && f.fees == Seq(fee, fee))
        case None => assert(false)
      }

      info("query rings: sort")
      val q5 = GetRings.Req(sort = SortingType.DESC)
      val r5 = Await.result(
        singleRequest(q5, "get_rings").mapTo[GetRings.Res],
        5.second)
      assert(r5.rings.length == 3 && r5.total == 3)
      val q6 = GetRings.Req(sort = SortingType.ASC)
      val r6 = Await.result(
        singleRequest(q6, "get_rings").mapTo[GetRings.Res],
        5.second)
      assert(r6.rings.length == 3 && r6.total == 3)
      assert(r5.rings.head == r6.rings.last)

      info("query rings: skip")
      val q7 = GetRings.Req(skip = Some(Paging(skip = 1, size = 10)))
      val r7 = Await.result(
        singleRequest(q7, "get_rings").mapTo[GetRings.Res],
        5.second)
      assert(r7.rings.length == 2 && r7.total == 3)
    }
  }

  val owner1 = "0x4385adb3e6b88a6691ae24c8c317b7327d91a8ad"

  val hash1 =
    "0x36ea537d8f02693c7a0c4c0cd590906cfbbe654a96668555e50277b8bec7cc55"
  val tokenS1 = "0x97241525fe425C90eBe5A41127816dcFA5954b06"
  val tokenB1 = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc"
  val height1 = 11

  val owner2 = "0x373d6d769154edbba3049ffa4b40716b276dada8"

  val hash2 =
    "0xba2a688aae3307d96e39c03bfdba97889ed55b4992c8fb121b026273184d7ccc"
  val tokenS2 = "0xa1c95e17f629d8bc5985f3f997760a575d56b0c2"
  val tokenB2 = "0x14b0846eb7fe70cc155138e0da9ab990ffeacc23"
  val height2 = 22
  val miner = "0x624d520bab2e4ad83935fa503fb130614374e850"
  val wallet = "0x74febeff16769960528c7f22acfa8e6df7f9cd53"
  val feeRecipient = "0x624d520bab2e4ad83935fa503fb130614374e850"

  val hash3 =
    "0x30f3c30128432ef6b0bbf3d89002a6af96768f74390ff3061a4f548848e669dc"
  val height3 = 33

  val fees = Ring.Fees(
    Seq(
      Trade.Fee(
        tokenFee = "0x97241525fe425C90eBe5A41127816dcFA5954b06",
        amountFee = ByteString.copyFrom("10", "UTF-8"),
        feeAmountS = ByteString.copyFrom("11", "UTF-8"),
        feeAmountB = ByteString.copyFrom("12", "UTF-8"),
        feeRecipient = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc",
        waiveFeePercentage = 10,
        walletSplitPercentage = 5),
      Trade.Fee(
        tokenFee = "0x2d92e8a4556e9100f1bd7709293f122f69d2cd2b",
        amountFee = ByteString.copyFrom("20", "UTF-8"),
        feeAmountS = ByteString.copyFrom("21", "UTF-8"),
        feeAmountB = ByteString.copyFrom("22", "UTF-8"),
        feeRecipient = "0xa1c95e17f629d8bc5985f3f997760a575d56b0c2",
        waiveFeePercentage = 8,
        walletSplitPercentage = 2)))

  val header = EventHeader(
    txHash = hash1,
    txStatus = TxStatus.TX_STATUS_SUCCESS,
    blockHash = hash1,
    blockNumber = height1)

  val fill1 = OrderFilledEvent(
    Some(header),
    owner1,
    hash1,
    "0x0",
    hash1,
    10,
    0,
    ByteString.copyFrom("10", "UTF-8"),
    ByteString.copyFrom("1", "UTF-8"),
    ByteString.copyFrom("2", "UTF-8"),
    tokenS1,
    tokenB1,
    wallet,
    1,
    1,
    "0x97241525fe425C90eBe5A41127816dcFA5954b06",
    ByteString.copyFrom("3", "UTF-8"),
    ByteString.copyFrom("5", "UTF-8"),
    ByteString.copyFrom("6", "UTF-8"))

  val fill2 = OrderFilledEvent(
    Some(header.copy(txHash = hash2, blockHash = hash2, blockNumber = height2)),
    owner2,
    hash2,
    "0x0",
    hash2,
    11,
    0,
    ByteString.copyFrom("13", "UTF-8"),
    ByteString.copyFrom("2", "UTF-8"),
    ByteString.copyFrom("2", "UTF-8"),
    tokenS2,
    tokenB2,
    wallet,
    1,
    1,
    "0x97241525fe425C90eBe5A41127816dcFA5954b06",
    ByteString.copyFrom("3", "UTF-8"),
    ByteString.copyFrom("5", "UTF-8"),
    ByteString.copyFrom("6", "UTF-8"))

  val fill3 = OrderFilledEvent(
    Some(header.copy(txHash = hash3, blockHash = hash3, blockNumber = height2)),
    owner1,
    hash3,
    "0x0",
    hash3,
    12,
    0,
    ByteString.copyFrom("53", "UTF-8"),
    ByteString.copyFrom("7", "UTF-8"),
    ByteString.copyFrom("3", "UTF-8"),
    tokenS2,
    tokenB2,
    wallet,
    1,
    1,
    "0x97241525fe425C90eBe5A41127816dcFA5954b06",
    ByteString.copyFrom("3", "UTF-8"),
    ByteString.copyFrom("5", "UTF-8"),
    ByteString.copyFrom("6", "UTF-8"))

  private def testSaveSomeRings() = {
    val tempTokens1 = fill1.tokenS
    val tempTokenb1 = fill1.tokenB
    val fills1 = Seq(
      fill1,
      fill1.copy(tokenS = tempTokenb1, tokenB = tempTokens1, fillIndex = 1))
    val e1 =
      RingMinedEvent(Some(header), height1, hash1, feeRecipient, fills1, miner)

    val tempTokens2 = fill2.tokenS
    val tempTokenb2 = fill2.tokenB
    val fills2 = Seq(
      fill2,
      fill2.copy(tokenS = tempTokenb2, tokenB = tempTokens2, fillIndex = 1))
    val e2 = RingMinedEvent(
      Some(
        header.copy(txHash = hash2, blockHash = hash2, blockNumber = height2)),
      height2,
      hash2,
      feeRecipient,
      fills2,
      miner)

    val fills3 = Seq(
      fill3,
      fill3.copy(tokenS = fill2.tokenB, tokenB = fill2.tokenS, fillIndex = 1))
    val e3 = RingMinedEvent(
      Some(
        header.copy(txHash = hash3, blockHash = hash3, blockNumber = height3)),
      height3,
      hash3,
      feeRecipient,
      fills3,
      miner)
    ringAndTradePersistActor ! e1
    ringAndTradePersistActor ! e2
    ringAndTradePersistActor ! e3
  }

}
