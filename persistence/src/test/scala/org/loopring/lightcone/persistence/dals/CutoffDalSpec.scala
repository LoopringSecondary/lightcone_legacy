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

import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.proto.core.XCutoff
import org.loopring.lightcone.proto.persistence._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

class CutoffDalSpec extends DalSpec[CutoffDal] {
  def getDal = new CutoffDalImpl()
  val timeProvider = new SystemTimeProvider()

  private def testCutoffByBrokers(
    brokers: Seq[String],
    tradingPair: String,
    time: Long,
    blockHeight: Long
  ): Future[Seq[XPersistenceError]] = {
    for {
      result ← Future.sequence(brokers.map { broker ⇒
        dal.saveCutoff(XCutoff(
          txHash = broker,
          cutoffType = XCutoff.XType.BROKER,
          broker = broker,
          tradingPair = tradingPair,
          cutoff = time,
          blockHeight = blockHeight
        ))
      })
    } yield result
  }

  private def testCutoffByOwners(
    owners: Seq[String],
    tradingPair: String,
    time: Long,
    blockHeight: Long
  ): Future[Seq[XPersistenceError]] = {
    for {
      result ← Future.sequence(owners.map { owner ⇒
        dal.saveCutoff(XCutoff(
          txHash = owner,
          cutoffType = XCutoff.XType.OWNER,
          owner = owner,
          tradingPair = tradingPair,
          cutoff = time,
          blockHeight = blockHeight
        ))
      })
    } yield result
  }

  "saveCutoff" must "save some cutoffs" in {
    val brokers1 = Seq(
      "0x-savecutoff-broker1",
      "0x-savecutoff-broker2",
      "0x-savecutoff-broker3",
      "0x-savecutoff-broker4",
      "0x-savecutoff-broker5",
      "0x-savecutoff-broker6",
      "0x-savecutoff-broker7",
      "0x-savecutoff-broker8"
    )
    val brokers2 = Seq(
      "0x-savecutoff-broker11",
      "0x-savecutoff-broker12",
      "0x-savecutoff-broker13",
      "0x-savecutoff-broker14",
      "0x-savecutoff-broker15",
      "0x-savecutoff-broker16",
      "0x-savecutoff-broker17",
      "0x-savecutoff-broker18"
    )
    val owners = Seq(
      "0x-savecutoff-owner1",
      "0x-savecutoff-owner2",
      "0x-savecutoff-owner3",
      "0x-savecutoff-owner4",
      "0x-savecutoff-owner5",
      "0x-savecutoff-owner6",
      "0x-savecutoff-owner7",
      "0x-savecutoff-owner8",
      "0x-savecutoff-owner9",
      "0x-savecutoff-owner10"
    )
    val result = for {
      _ ← testCutoffByBrokers(brokers1, "LRC-WETH", timeProvider.getTimeSeconds(), 1l)
      _ ← testCutoffByBrokers(brokers2, "AAA-WETH", timeProvider.getTimeSeconds(), 1l)
      _ ← testCutoffByOwners(owners, "OMG-LRC", timeProvider.getTimeSeconds(), 1l)
      queryBrokers ← dal.getCutoffs(Some(XCutoff.XType.BROKER), None, None)
      queryBroker ← dal.getCutoffs(
        Some(XCutoff.XType.BROKER),
        Some(XCutoffBy(XCutoffBy.Value.Broker("0x-savecutoff-broker4"))), None
      )
      queryBrokerPair ← dal.getCutoffs(
        Some(XCutoff.XType.BROKER),
        Some(XCutoffBy(XCutoffBy.Value.Broker("0x-savecutoff-broker8"))), Some("XYZ-WETH")
      )
      queryOwner ← dal.getCutoffs(
        Some(XCutoff.XType.OWNER),
        Some(XCutoffBy(XCutoffBy.Value.Owner("0x-savecutoff-owner9")))
      )
      queryPair ← dal.getCutoffs(None, None, Some("LRC-WETH"))
      _ ← dal.obsolete(0l) // clear datas for next spec method
    } yield (queryBrokers, queryBroker, queryBrokerPair, queryOwner, queryPair)
    val res = Await.result(
      result.mapTo[(Seq[XCutoff], Seq[XCutoff], Seq[XCutoff], Seq[XCutoff], Seq[XCutoff])],
      5.second
    )
    val x = res._1.length === 16 && res._2.length === 1 && res._3.length === 0 && res._4.length === 1 &&
      res._5.length === 8
    x should be(true)
  }

  "hasCutoff" must "has some cutoffs" in {
    val brokers1 = Seq(
      "0x-hasCutoff-broker1",
      "0x-hasCutoff-broker2",
      "0x-hasCutoff-broker3",
      "0x-hasCutoff-broker4",
      "0x-hasCutoff-broker5",
      "0x-hasCutoff-broker6",
      "0x-hasCutoff-broker7",
      "0x-hasCutoff-broker8"
    )
    val brokers2 = Seq(
      "0x-hasCutoff-broker11",
      "0x-hasCutoff-broker12",
      "0x-hasCutoff-broker13",
      "0x-hasCutoff-broker14",
      "0x-hasCutoff-broker15",
      "0x-hasCutoff-broker16",
      "0x-hasCutoff-broker17",
      "0x-hasCutoff-broker18"
    )
    val owners = Seq(
      "0x-hasCutoff-owner1",
      "0x-hasCutoff-owner2",
      "0x-hasCutoff-owner3",
      "0x-hasCutoff-owner4",
      "0x-hasCutoff-owner5",
      "0x-hasCutoff-owner6",
      "0x-hasCutoff-owner7",
      "0x-hasCutoff-owner8",
      "0x-hasCutoff-owner9",
      "0x-hasCutoff-owner10"
    )
    val result = for {
      _ ← testCutoffByBrokers(brokers1, "LRC-WETH", timeProvider.getTimeSeconds(), 1l)
      _ ← testCutoffByBrokers(brokers2, "LRC-WETH", timeProvider.getTimeSeconds() - 1000, 1l)
      _ ← testCutoffByOwners(owners, "OMG-LRC", timeProvider.getTimeSeconds(), 1l)
      queryBrokers ← dal.hasCutoffs(Some(XCutoff.XType.BROKER), None, None, None)
      queryBrokerTime ← dal.hasCutoffs(Some(XCutoff.XType.BROKER), None, None,
        Some(timeProvider.getTimeSeconds() - 100))
      queryBrokerPair ← dal.hasCutoffs(
        Some(XCutoff.XType.BROKER),
        Some(XCutoffBy(XCutoffBy.Value.Broker("0x-hasCutoff-broker8"))),
        Some("LRC-WETH"), Some(timeProvider.getTimeSeconds() - 100)
      )
      queryOwner ← dal.hasCutoffs(
        Some(XCutoff.XType.OWNER),
        Some(XCutoffBy(XCutoffBy.Value.Owner("0x-hasCutoff-owner9"))), None, None
      )
      queryPair ← dal.hasCutoffs(None, None, Some("LRC-WETH"), None)
      _ ← dal.obsolete(0l) // clear datas for next spec method
    } yield (queryBrokers, queryBrokerTime, queryBrokerPair, queryOwner, queryPair)
    val res = Await.result(result.mapTo[(Boolean, Boolean, Boolean, Boolean, Boolean)], 5.second)
    val x = res._1 && res._2 && res._3 && res._4 && res._5
    x should be(true)
  }

  "obsolete" must "obsolete blocks above height 1" in {
    val brokers1 = Seq(
      "0x-obsolete-broker1",
      "0x-obsolete-broker2",
      "0x-obsolete-broker3",
      "0x-obsolete-broker4",
      "0x-obsolete-broker5",
      "0x-obsolete-broker6",
      "0x-obsolete-broker7",
      "0x-obsolete-broker8"
    )
    val brokers2 = Seq(
      "0x-obsolete-broker11",
      "0x-obsolete-broker12",
      "0x-obsolete-broker13",
      "0x-obsolete-broker14",
      "0x-obsolete-broker15",
      "0x-obsolete-broker16",
      "0x-obsolete-broker17",
      "0x-obsolete-broker18"
    )
    val owners = Seq(
      "0x-obsolete-owner1",
      "0x-obsolete-owner2",
      "0x-obsolete-owner3",
      "0x-obsolete-owner4",
      "0x-obsolete-owner5",
      "0x-obsolete-owner6",
      "0x-obsolete-owner7",
      "0x-obsolete-owner8",
      "0x-obsolete-owner9",
      "0x-obsolete-owner10"
    )
    val result = for {
      _ ← testCutoffByBrokers(brokers1, "LRC-WETH", timeProvider.getTimeSeconds(), 100l)
      _ ← testCutoffByBrokers(brokers2, "ABC-WETH", timeProvider.getTimeSeconds() - 1000, 10l)
      _ ← testCutoffByOwners(owners, "OMG-LRC", timeProvider.getTimeSeconds(), 30l)
      _ ← dal.obsolete(30l)
      query ← dal.getCutoffs(None, None, None)
    } yield query
    val res = Await.result(result.mapTo[Seq[XCutoff]], 5.second)
    res.length should be(8)
  }
}
