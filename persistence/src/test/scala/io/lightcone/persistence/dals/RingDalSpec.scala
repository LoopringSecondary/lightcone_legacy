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
import io.lightcone.ethereum.persistence._
import io.lightcone.persistence._
import scala.concurrent.Await
import scala.concurrent.duration._

class RingDalSpec extends DalSpec[RingDal] {

  def getDal = new RingDalImpl()

  "ringService" must "save and query correctly" in {
    info("save some rings")
    val r1 = Await.result(testSaveSomeRings().mapTo[Seq[ErrorCode]], 5.second)
    assert(r1.length == 3 && !r1.exists(_ != ErrorCode.ERR_NONE))

    info(
      "save a duplicate ring(txHash, ringHash and ringIndex) should return error"
    )
    val r2 = Await.result(testDuplicateSave().mapTo[ErrorCode], 5.second)
    assert(r2 == ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT)

    info("query rings: by ringHash and ringIndex")
    val r3 = Await.result(
      dal.getRings(Some(hash2), None, SortingType.ASC, None).mapTo[Seq[Ring]],
      5.second
    )
    val c3 =
      Await.result(dal.countRings(Some(hash2), None).mapTo[Int], 5.second)
    assert(r3.length == 1 && c3 == 1)
    val r4 = Await.result(
      dal.getRings(None, Some(11), SortingType.ASC, None).mapTo[Seq[Ring]],
      5.second
    )
    val c4 = Await.result(dal.countRings(None, Some(11)).mapTo[Int], 5.second)
    assert(r4.length == 1 && c4 == 1)
    assert(r3.head == r4.head)
    r3.head.fees match {
      case Some(f) =>
        assert(f.fees.length == 2)
        f.fees foreach {
          case fee: Fill.Fee if fee == fee1 => assert(true)
          case fee: Fill.Fee if fee == fee2 => assert(true)
          case _                            => assert(false)
        }
      case None => assert(false)
    }

    info("query rings: sort")
    val r5 = Await.result(
      dal
        .getRings(None, None, SortingType.DESC, Some(CursorPaging(size = 10)))
        .mapTo[Seq[Ring]],
      5.second
    )
    val c5 = Await.result(dal.countRings(None, None).mapTo[Int], 5.second)
    assert(r5.length == 3 && c5 == 3)
    val r6 = Await.result(
      dal
        .getRings(None, None, SortingType.ASC, Some(CursorPaging(size = 10)))
        .mapTo[Seq[Ring]],
      5.second
    )
    val c6 = Await.result(dal.countRings(None, None).mapTo[Int], 5.second)
    assert(r6.length == 3 && c6 == 3)
    assert(r5.head == r6.last)

    info("query rings: skip")
    val r7 = Await.result(
      dal
        .getRings(
          None,
          None,
          SortingType.ASC,
          Some(CursorPaging(cursor = 10, size = 10))
        )
        .mapTo[Seq[Ring]],
      5.second
    )
    val c7 = Await.result(dal.countRings(None, None).mapTo[Int], 5.second)
    assert(r7.length == 2 && c7 == 3)
  }

  val hash1 =
    "0x36ea537d8f02693c7a0c4c0cd590906cfbbe654a96668555e50277b8bec7cc55"

  val hash2 =
    "0xba2a688aae3307d96e39c03bfdba97889ed55b4992c8fb121b026273184d7ccc"

  val hash3 =
    "0x30f3c30128432ef6b0bbf3d89002a6af96768f74390ff3061a4f548848e669dc"
  val miner = "0x624d520bab2e4ad83935fa503fb130614374e850"

  val fee1 = Fill.Fee(
    tokenFee = "0x97241525fe425C90eBe5A41127816dcFA5954b06",
    amountFee = BigInt(10),
    feeAmountS = BigInt(11),
    feeAmountB = BigInt(12),
    feeRecipient = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc",
    waiveFeePercentage = 10,
    walletSplitPercentage = 5
  )

  val fee2 = Fill.Fee(
    tokenFee = "0x2d92e8a4556e9100f1bd7709293f122f69d2cd2b",
    amountFee = BigInt(20),
    feeAmountS = BigInt(21),
    feeAmountB = BigInt(22),
    feeRecipient = "0xa1c95e17f629d8bc5985f3f997760a575d56b0c2",
    waiveFeePercentage = 8,
    walletSplitPercentage = 2
  )

  val fees = Ring.Fees(Seq(fee1, fee2))

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
    dal.saveRings(rings)
  }

  private def testDuplicateSave() = {
    dal.saveRing(
      Ring(
        ringHash = hash3,
        ringIndex = 12,
        fillsAmount = 2,
        miner = miner,
        txHash = hash3,
        fees = Some(fees),
        blockHeight = 100
      )
    )
  }
}
