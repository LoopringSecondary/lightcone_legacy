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

import io.lightcone.persistence.dals._
import io.lightcone.relayer.data.GetRings._
import io.lightcone.core._
import scala.concurrent._
import scala.concurrent.duration._

class RingServiceSpec extends ServiceSpec[RingService] {

  implicit var dal: RingDal = _

  def getService = {
    dal = new RingDalImpl()
    new RingServiceImpl()
  }

  def createTables(): Unit = dal.createTable()

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
    val q3 = Req(filter = Req.Filter.RingHash(hash2))
    val r3 = Await.result(service.getRings(q3).mapTo[Seq[Ring]], 5.second)
    val c3 = Await.result(service.countRings(q3).mapTo[Int], 5.second)
    assert(r3.length == 1 && c3 == 1)
    val q4 = Req(filter = Req.Filter.RingIndex(11))
    val r4 = Await.result(service.getRings(q4).mapTo[Seq[Ring]], 5.second)
    val c4 = Await.result(service.countRings(q4).mapTo[Int], 5.second)
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
    val q5 = Req(sort = SortingType.DESC)
    val r5 = Await.result(service.getRings(q5).mapTo[Seq[Ring]], 5.second)
    val c5 = Await.result(service.countRings(q5).mapTo[Int], 5.second)
    assert(r5.length == 3 && c5 == 3)
    val q6 = Req(sort = SortingType.ASC)
    val r6 = Await.result(service.getRings(q6).mapTo[Seq[Ring]], 5.second)
    val c6 = Await.result(service.countRings(q6).mapTo[Int], 5.second)
    assert(r6.length == 3 && c6 == 3)
    assert(r5.head == r6.last)

    info("query rings: skip")
    val q7 = Req(skip = Some(Paging(skip = 1, size = 10)))
    val r7 = Await.result(service.getRings(q7).mapTo[Seq[Ring]], 5.second)
    val c7 = Await.result(service.countRings(q7).mapTo[Int], 5.second)
    assert(r7.length == 2 && c7 == 3)

    info("obsolete")
    Await.result(service.obsolete(120L).mapTo[Unit], 5.second)
    val c11 = Await.result(service.countRings(Req()).mapTo[Int], 5.second)
    assert(c11 == 2)
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
    service.saveRings(rings)
  }

  private def testDuplicateSave() = {
    service.saveRing(
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
