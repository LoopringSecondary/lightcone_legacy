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

import org.loopring.lightcone.lib.SystemTimeProvider
import org.loopring.lightcone.persistence.dals.OrdersCutoffDalImpl
import org.loopring.lightcone.persistence.service.{
  OrdersCutoffService,
  OrdersCutoffServiceImpl
}
import org.loopring.lightcone.proto._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class OrdersCutoffServiceSpec extends ServiceSpec[OrdersCutoffService] {
  def getService = new OrdersCutoffServiceImpl()

  def createTables(): Future[Any] =
    for {
      r ← new OrdersCutoffDalImpl().createTable()
    } yield r
  val timeProvider = new SystemTimeProvider()

  private def testCutoffByBrokers(
      brokers: Seq[String],
      tradingPair: Option[String],
      time: Long,
      blockHeight: Long
    ): Future[Seq[XErrorCode]] = {
    for {
      result ← Future.sequence(brokers.map { broker ⇒
        service.saveCutoff(
          XOrdersCutoffEvent(
            txHash = broker,
            broker = broker,
            tradingPair = tradingPair.getOrElse(""),
            cutoff = time,
            blockHeight = blockHeight
          )
        )
      })
    } yield result
  }

  private def testCutoffByOwners(
      owners: Seq[String],
      tradingPair: Option[String],
      time: Long,
      blockHeight: Long
    ): Future[Seq[XErrorCode]] = {
    for {
      result ← Future.sequence(owners.map { owner ⇒
        service.saveCutoff(
          XOrdersCutoffEvent(
            txHash = owner,
            owner = owner,
            tradingPair = tradingPair.getOrElse(""),
            cutoff = time,
            blockHeight = blockHeight
          )
        )
      })
    } yield result
  }

  private def testCutoffByBrokerAndOwners(
      brokers: Seq[String],
      tradingPair: Option[String],
      time: Long,
      blockHeight: Long
    ): Future[Seq[XErrorCode]] = {
    for {
      result ← Future.sequence(brokers.map { broker ⇒
        service.saveCutoff(
          XOrdersCutoffEvent(
            txHash = broker,
            broker = broker,
            owner = broker,
            tradingPair = tradingPair.getOrElse(""),
            cutoff = time,
            blockHeight = blockHeight
          )
        )
      })
    } yield result
  }

  "printSQL" must "save some cutoffs" in {
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
    val now = timeProvider.getTimeSeconds()
    val result = for {
      _ ← testCutoffByBrokers(brokers1, Some("0x-tradingpair-01"), now, 1L) //broker1 取消了 market："0x-tradingpair-01"
      q ← service.hasCutoff(
        Some("0x-savecutoff-broker5"),
        "0x-savecutoff-broker5",
        "0x-tradingpair-01",
        10000000L
      ) // true
    } yield q
    val res = Await.result(
      result.mapTo[Boolean],
      5.second
    )
    res should be(true)
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
    val owner1 = Seq(
      "0x-savecutoff-owner1",
      "0x-savecutoff-owner2",
      "0x-savecutoff-owner3"
    )
    val owner2 = Seq(
      "0x-savecutoff-owner7",
      "0x-savecutoff-owner8",
      "0x-savecutoff-owner9"
    )
    val brokerOwners = Seq(
      "0x-savecutoff-owner11",
      "0x-savecutoff-owner12",
      "0x-savecutoff-owner13",
      "0x-savecutoff-owner14",
      "0x-savecutoff-owner15",
      "0x-savecutoff-owner16"
    )
    val now = timeProvider.getTimeSeconds()
    val result = for {
      _ ← testCutoffByBrokers(brokers1, Some("0x-tradingpair-01"), now, 1L) //broker1 取消了 market："0x-tradingpair-01"
      q1 ← service.hasCutoff(
        Some("0x-savecutoff-broker5"),
        "0x-savecutoff-broker5",
        "0x-tradingpair-01",
        10000000L
      ) // true
      q2 ← service.hasCutoff(
        Some("0x-savecutoff-broker5"),
        "0x-savecutoff-broker5",
        "0x-tradingpair-02",
        10000000L
      ) // false
      _ ← testCutoffByBrokers(brokers2, None, 20000000L, 1L) // broker2 取消了所有
      q3 ← service.hasCutoff(
        Some("0x-savecutoff-broker13"),
        "0x-savecutoff-broker13",
        "0x-tradingpair-01",
        10000000L
      ) // true
      q4 ← service.hasCutoff(
        Some("0x-savecutoff-broker13"),
        "0x-savecutoff-broker13",
        "0x-tradingpair-02",
        10000000L
      ) // true
      q5 ← service.hasCutoff(
        Some("0x-savecutoff-broker13"),
        "0x-savecutoff-broker-not-exist",
        "0x-tradingpair-not-exist",
        10000000L
      ) // true
      _ ← testCutoffByBrokerAndOwners(
        brokerOwners,
        Some("0x-tradingpair-04"),
        20000000L,
        1L
      ) // broker==owner取消了market:"0x-tradingpair-04"
      q6 ← service.hasCutoff(
        Some("0x-savecutoff-owner11"),
        "0x-savecutoff-owner11",
        "0x-tradingpair-04",
        10000000L
      ) // true
      q7 ← service.hasCutoff(
        Some("0x-savecutoff-owner11"),
        "0x-savecutoff-owner11",
        "0x-tradingpair-not-exist",
        10000000L
      ) // false
      q8 ← service.hasCutoff(
        Some("0x-savecutoff-owner11"),
        "0x-savecutoff-broker-not-exist",
        "0x-tradingpair-not-exist",
        10000000L
      ) // false
      _ ← testCutoffByOwners(owner1, None, now, 1L) // owner 取消了所有
      q9 ← service.hasCutoff(
        None,
        "0x-savecutoff-owner2",
        "0x-tradingpair-not-exist",
        10000000L
      ) // true
      _ ← testCutoffByOwners(owner2, Some("0x-tradingpair-06"), now, 1L) // owner 取消了market:0x-tradingpair-06
      q10 ← service.hasCutoff(
        None,
        "0x-savecutoff-owner9",
        "0x-tradingpair-06",
        10000000L
      ) // true
      q11 ← service.hasCutoff(
        None,
        "0x-savecutoff-owner9",
        "0x-tradingpair-not-exist",
        10000000L
      ) // false
      _ ← service.obsolete(0L) // clear datas for next spec method
    } yield (q1, q2, q3, q4, q5, q6, q7, q8, q9, q10, q11)
    val res = Await.result(
      result.mapTo[
        (
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean,
            Boolean
        )
      ],
      5.second
    )
    val x = res._1 === true && res._2 === false && res._3 === true && res._4 === true &&
      res._5 === true && res._6 === true && res._7 === false && res._8 === false &&
      res._9 === true && res._10 === true && res._11 === false
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
    val now = timeProvider.getTimeSeconds()
    val result = for {
      _ ← testCutoffByBrokers(brokers1, Some("LRC-WETH"), now, 100L)
      _ ← testCutoffByBrokers(brokers2, Some("ABC-WETH"), now - 1000, 10L) //available
      _ ← testCutoffByOwners(owners, None, now, 30L)
      _ ← service.obsolete(30L)
      q1 ← service.hasCutoff(
        Some("0x-obsolete-broker13"),
        "0x-obsolete-broker13",
        "pari-not-exist",
        now - 2000
      ) //false
      q2 ← service.hasCutoff(
        Some("0x-obsolete-broker13"),
        "0x-obsolete-broker13",
        "ABC-WETH",
        now - 2000
      ) //true
    } yield (q1, q2)
    val res = Await.result(result.mapTo[(Boolean, Boolean)], 5.second)
    res._1 === false && res._2 === true should be(true)
  }
}
