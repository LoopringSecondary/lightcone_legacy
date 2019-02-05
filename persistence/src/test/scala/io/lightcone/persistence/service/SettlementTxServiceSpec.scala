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

package io.lightcone.persistence.service

import io.lightcone.lib._
import io.lightcone.persistence.dals._
import io.lightcone.core._
import io.lightcone.proto._

import scala.concurrent._
import scala.concurrent.duration._

class SettlementTxServiceSpec extends ServiceSpec[SettlementTxService] {

  implicit var dal: SettlementTxDal = _

  def getService = {
    dal = new SettlementTxDalImpl()
    new SettlementTxServiceImpl()
  }

  def createTables(): Unit = dal.createTable()

  private def testSave(
      txHash: String,
      owner: String,
      nonce: Long,
      status: SettlementTx.Status
    ): Future[PersistSettlementTx.Res] = {
    service.saveTx(
      PersistSettlementTx.Req(
        Some(
          SettlementTx(
            txHash = txHash,
            from = owner,
            nonce = nonce,
            status = status,
            createAt = timeProvider.getTimeSeconds()
          )
        )
      )
    )
  }

  "savePendingTxs" must "save some pending txs" in {
    val txHashes = Set(
      "0x-savepending-01",
      "0x-savepending-02",
      "0x-savepending-03",
      "0x-savepending-04",
      "0x-savepending-05"
    )
    val owner = "0x-test1-owner"
    val time = timeProvider.getTimeSeconds() + 1000
    val result = for {
      _ <- Future.sequence(txHashes.map { hash =>
        testSave(hash, owner, 1, SettlementTx.Status.PENDING)
      })
      query <- service.getPendingTxs(
        GetPendingTxs.Req(owner = owner, timeBefore = time)
      )
    } yield query
    val res = Await.result(result.mapTo[GetPendingTxs.Res], 5.second)
    res.txs.length == 1 should be(true)
  }

  "getPending" must "get some pending txs" in {
    val txHashes = Set(
      "0x-getpending-state0-01",
      "0x-getpending-state0-02",
      "0x-getpending-state0-03",
      "0x-getpending-state0-04",
      "0x-getpending-state0-05"
    )
    val mocks = Set(
      "0x-getpending-state1-01",
      "0x-getpending-state1-02",
      "0x-getpending-state1-03",
      "0x-getpending-state1-04"
    )
    val owner = "0x-test2-owner"
    val time = timeProvider.getTimeSeconds() + 1000
    val result = for {
      _ <- Future.sequence(txHashes.map { hash =>
        testSave(hash, owner, 1, SettlementTx.Status.PENDING)
      })
      _ <- Future.sequence(mocks.map { hash =>
        testSave(hash, owner, 2, SettlementTx.Status.PENDING)
      })
      query1 <- service.getPendingTxs(
        GetPendingTxs.Req(owner = owner, timeBefore = time)
      )
    } yield query1
    val res = Await.result(result.mapTo[GetPendingTxs.Res], 5.second)
    res.txs.length === 2 should be(true)
  }

  "updatePendingInBlock" must "update pending txs with BLOCK status" in {
    val txHashes = Set(
      "0x-updateblock-01",
      "0x-updateblock-02",
      "0x-updateblock-03",
      "0x-updateblock-04",
      "0x-updateblock-05"
    )
    val owner = "0x-test3-owner"
    val time = timeProvider.getTimeSeconds() + 1000
    val result = for {
      _ <- Future.sequence(txHashes.map { hash =>
        testSave(hash, owner, 1, SettlementTx.Status.PENDING)
      })
      query1 <- service.getPendingTxs(
        GetPendingTxs.Req(owner = owner, timeBefore = time)
      )
      _ <- service.updateInBlock(
        UpdateTxInBlock
          .Req(txHash = "0x-updateblock-03", from = owner, nonce = 1)
      )
      query2 <- service.getPendingTxs(
        GetPendingTxs.Req(owner = owner, timeBefore = time)
      )
    } yield (query1, query2)
    val res =
      Await.result(
        result.mapTo[(GetPendingTxs.Res, GetPendingTxs.Res)],
        5.second
      )
    res._1.txs.length === 1 && res._2.txs.length === 0 should be(true)
  }

  "update status with a not exist tx hash" must "update failed" in {
    val txHashes = Set(
      "0x-updateblock-01",
      "0x-updateblock-02",
      "0x-updateblock-03",
      "0x-updateblock-04",
      "0x-updateblock-05"
    )
    val owner = "0x-test3-owner"
    val time = timeProvider.getTimeSeconds() + 1000
    val result = for {
      _ <- Future.sequence(txHashes.map { hash =>
        testSave(hash, owner, 1, SettlementTx.Status.PENDING)
      })
      updated <- service.updateInBlock(
        UpdateTxInBlock.Req(txHash = "0x-tx-not-exist", from = owner, nonce = 1)
      )
    } yield updated
    val res =
      try {
        Await.result(result.mapTo[UpdateTxInBlock.Res], 5.second)
      } catch {
        case e: ErrorException => e
        case m: Throwable      => m
      }
    res match {
      case e: ErrorException =>
        assert(e.error.code === ErrorCode.ERR_PERSISTENCE_UPDATE_FAILED)
      case _ => assert(false)
    }
  }

}
