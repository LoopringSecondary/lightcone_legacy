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
import org.loopring.lightcone.persistence.dals.OrdersCancelledEventDalImpl
import org.loopring.lightcone.persistence.service._
import org.loopring.lightcone.proto._
import scala.concurrent._
import scala.concurrent.duration._

class OrdersCancelledEventServiceSpec
    extends ServiceSpec[OrdersCancelledEventService] {
  def getService = new OrdersCancelledEventServiceImpl()
  val hash = "0x-cancelorder-01"

  def createTables(): Future[Any] =
    for {
      r <- new OrdersCancelledEventDalImpl().createTable()
    } yield r

  private def testSave(
      hash: String,
      blockHeight: Long
    ): Future[ErrorCode] = {
    val now = timeProvider.getTimeMillis
    service.saveCancelOrder(
      OrdersCancelledEvent(
        txHash = hash,
        brokerOrOwner = hash,
        orderHash = hash,
        blockHeight = blockHeight
      )
    )
  }

  private def testSaves(
      hashes: Set[String],
      blockHeight: Long
    ): Future[Set[ErrorCode]] = {
    for {
      result <- Future.sequence(hashes.map { hash ⇒
        testSave(hash, blockHeight)
      })
    } yield result
  }

  "save" must "save a cancel order" in {
    val result = for {
      _ <- testSave(hash, 1L)
      query <- service.hasCancelled(hash)
      _ <- service.obsolete(0) //clear data
    } yield query
    val res = Await.result(result.mapTo[Boolean], 5.second)
    res should be(true)
  }

  "obsolete" must "obsolete successfully" in {
    val owners1 = Set(
      "0x-obsolete-01",
      "0x-obsolete-02",
      "0x-obsolete-03",
      "0x-obsolete-04",
      "0x-obsolete-05",
      "0x-obsolete-06"
    )
    val owners2 = Set(
      "0x-obsolete-11",
      "0x-obsolete-12",
      "0x-obsolete-13",
      "0x-obsolete-14",
      "0x-obsolete-15",
      "0x-obsolete-16"
    )
    val result = for {
      _ <- testSaves(owners1, 100L)
      queryNone <- service.hasCancelled(hash)
      _ <- testSaves(owners2, 101L)
      _ <- service.obsolete(101L)
      queryOne <- service.hasCancelled("0x-obsolete-04")
    } yield (queryNone, queryOne)
    val res = Await.result(result.mapTo[(Boolean, Boolean)], 5.second)
    val x = res._1 === false && res._2 === true
    x should be(true)
  }
}
