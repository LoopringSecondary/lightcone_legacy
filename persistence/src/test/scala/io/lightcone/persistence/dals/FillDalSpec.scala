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

package io.lightcone.persistence.dals

import io.lightcone.core._
import io.lightcone.persistence._
import scala.concurrent.Await
import io.lightcone.ethereum.persistence._
import scala.concurrent.duration._

class FillDalSpec extends DalSpec[FillDal] {

  def getDal = new FillDalImpl()

  "fillService" must "save and query correctly" in {
    info("save some fills")
    val r1 = Await.result(testSaveSomeFills().mapTo[Seq[ErrorCode]], 5.second)
    assert(r1.length == 4)
    assert(r1.length == 4 && !r1.exists(_ != ErrorCode.ERR_NONE))

    info("save a duplicate fill(txHash and fillIndex) should return error")
    val r2 = Await.result(testDuplicateSave().mapTo[ErrorCode], 5.second)
    assert(r2 == ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT)

    info("query fills: by owner")
    val r3 = Await.result(
      dal
        .getFills(
          owner1,
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          None,
          "",
          "",
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c3 = Await.result(
      dal
        .countFills(
          owner1,
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          None,
          "",
          ""
        )
        .mapTo[Int],
      5.second
    )
    assert(r3.length == 2 && c3 == 2)

    info("query fills: sort")
    val r3_2 = Await.result(
      dal
        .getFills(
          owner1,
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          None,
          "",
          "",
          SortingType.DESC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    assert(r3_2.length == 2)
    assert(r3.head == r3_2.last)

    info("query fills: skip")
    val r3_3 = Await.result(
      dal
        .getFills(
          "",
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          None,
          "",
          "",
          SortingType.ASC,
          Some(Paging(skip = 1, size = 10))
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    assert(r3_3.length == 3)

    info("query fills: by owner and market")
    val r4 = Await.result(
      dal
        .getFills(
          owner1,
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          Some(MarketHash(MarketPair(tokenS1, tokenB1))),
          "",
          "",
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c4 = Await.result(
      dal
        .countFills(
          owner1,
          "",
          "",
          None,
          None,
          None,
          None,
          None,
          Some(MarketHash(MarketPair(tokenS1, tokenB1))),
          "",
          ""
        )
        .mapTo[Int],
      5.second
    )
    assert(r4.length == 2 && c4 == 2)

    info("query fills: by ring")
    val r6 = Await.result(
      dal
        .getFills(
          "",
          "",
          "",
          Some(hash2),
          None,
          None,
          None,
          None,
          None,
          "",
          "",
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c6 = Await.result(
      dal
        .countFills(
          "",
          "",
          "",
          Some(hash2),
          None,
          None,
          None,
          None,
          None,
          "",
          ""
        )
        .mapTo[Int],
      5.second
    )
    assert(r6.length == 2 && c6 == 2)

    val r7 = Await.result(
      dal
        .getFills(
          "",
          "",
          "",
          Some(hash2),
          Some(2),
          Some(1),
          None,
          None,
          None,
          "",
          "",
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c7 = Await.result(
      dal
        .countFills(
          "",
          "",
          "",
          Some(hash2),
          Some(2),
          Some(1),
          None,
          None,
          None,
          "",
          ""
        )
        .mapTo[Int],
      5.second
    )
    assert(r7.length == 1 && c7 == 1)
    val r8 = Await.result(
      dal
        .getFills(
          "",
          "",
          "",
          Some(hash2),
          Some(2),
          Some(2),
          None,
          None,
          None,
          "",
          "",
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c8 = Await.result(
      dal
        .countFills(
          "",
          "",
          "",
          Some(hash2),
          Some(2),
          Some(2),
          None,
          None,
          None,
          "",
          ""
        )
        .mapTo[Int],
      5.second
    )
    assert(r8.isEmpty && c8 == 0)

    info("query fills: full parameters")
    val r9 = Await.result(
      dal
        .getFills(
          owner1,
          hash1,
          hash1,
          Some(hash1),
          Some(1),
          Some(0),
          Some(tokenS1),
          Some(tokenB1),
          None,
          wallet,
          miner,
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c9 = Await.result(
      dal
        .countFills(
          owner1,
          hash1,
          hash1,
          Some(hash1),
          Some(1),
          Some(0),
          Some(tokenS1),
          Some(tokenB1),
          None,
          wallet,
          miner
        )
        .mapTo[Int],
      5.second
    )
    assert(r9.length == 1 && c9 == 1)
    val r10 = Await.result(
      dal
        .getFills(
          owner2,
          hash1,
          hash1,
          Some(hash1),
          Some(1),
          Some(0),
          Some(tokenS1),
          Some(tokenB1),
          None,
          wallet,
          miner,
          SortingType.ASC,
          None
        )
        .mapTo[Seq[Fill]],
      5.second
    )
    val c10 = Await.result(
      dal
        .countFills(
          owner2,
          hash1,
          hash1,
          Some(hash1),
          Some(1),
          Some(0),
          Some(tokenS1),
          Some(tokenB1),
          None,
          wallet,
          miner
        )
        .mapTo[Int],
      5.second
    )
    assert(r10.isEmpty && c10 == 0)
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

  private def testSaveSomeFills() = {
    val fills = Seq(
      Fill(
        txHash = hash1,
        orderHash = hash1,
        owner = owner1,
        tokenB = tokenB1,
        tokenS = tokenS1,
        amountB = BigInt(1),
        amountS = BigInt(10),
        blockHeight = 10,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 0,
        miner = miner,
        wallet = wallet
      ),
      Fill(
        txHash = hash1,
        orderHash = hash1,
        owner = owner1,
        tokenB = tokenS1,
        tokenS = tokenB1,
        amountB = BigInt(10),
        amountS = BigInt(1),
        blockHeight = 10,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 1,
        miner = miner,
        wallet = wallet
      ),
      Fill(
        txHash = hash2,
        orderHash = hash2,
        owner = owner2,
        tokenB = tokenB2,
        tokenS = tokenS2,
        amountB = BigInt(1),
        amountS = BigInt(10),
        blockHeight = 20,
        ringHash = hash2,
        ringIndex = 2,
        fillIndex = 0,
        miner = miner,
        wallet = wallet
      ),
      Fill(
        txHash = hash2,
        orderHash = hash2,
        owner = owner2,
        tokenB = tokenS2,
        tokenS = tokenB2,
        amountB = BigInt(10),
        amountS = BigInt(1),
        blockHeight = 20,
        ringHash = hash2,
        ringIndex = 2,
        fillIndex = 1,
        miner = miner,
        wallet = wallet
      )
    )
    dal.saveFills(fills)
  }

  private def testDuplicateSave() = {
    dal.saveFill(
      Fill(
        txHash = hash1,
        owner = owner1,
        tokenB = tokenS1,
        tokenS = tokenB1,
        amountB = BigInt(10),
        amountS = BigInt(1),
        blockHeight = 1,
        ringHash = hash1,
        ringIndex = 1,
        fillIndex = 1
      )
    )
  }
}
