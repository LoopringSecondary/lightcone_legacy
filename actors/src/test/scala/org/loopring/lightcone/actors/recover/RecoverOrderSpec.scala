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

package org.loopring.lightcone.actors.recover

import com.google.protobuf.ByteString
import org.loopring.lightcone.actors.core.{
  MultiAccountManagerActor,
  OrderRecoverCoordinator
}
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.{MarketHashProvider, SystemTimeProvider}
import org.loopring.lightcone.proto._
import scala.concurrent.{Await, Future}
import akka.pattern._
import akka.util.Timeout
import scala.concurrent.duration._
import org.loopring.lightcone.core.base._

class RecoverOrderSpec
    extends CommonSpec("""
                         |akka.cluster.roles=[
                         | "order_handler",
                         | "multi_account_manager",
                         | "market_manager",
                         | "orderbook_manager",
                         | "gas_price",
                         | "order_recover",
                         | "ring_settlement"]
                         |""".stripMargin)
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with EthereumQueryMockSupport
    with OrderGenerateSupport
    with RecoverSupport {
  val tokenS = "0xaaaaaa1"
  val tokenB = "0xbbbbbb1"
  val tokenFee = "0x-fee-token"
  val validSince = 1
  val validUntil = timeProvider.getTimeSeconds()

  private def testSave(
      hash: String,
      owner: String,
      status: XOrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int,
      validUntil: Int
    ): Future[Either[XRawOrder, XErrorCode]] = {
    val now = timeProvider.getTimeMillis
    val state =
      XRawOrder.State(createdAt = now, updatedAt = now, status = status)
    val fee = XRawOrder.FeeParams(
      tokenFee = LRC_TOKEN.address,
      amountFee = ByteString.copyFrom("1", "utf-8")
    )
    val param = XRawOrder.Params(validUntil = validUntil)
    val marketHash = MarketHashProvider.convert2Hex(tokenS, tokenB)
    val order = XRawOrder(
      owner = owner,
      hash = hash,
      version = 1,
      tokenS = tokenS,
      tokenB = tokenB,
      amountS = ByteString.copyFrom("11", "UTF-8"),
      amountB = ByteString.copyFrom("12", "UTF-8"),
      validSince = validSince,
      state = Some(state),
      feeParams = Some(fee),
      params = Some(param),
      marketHash = marketHash,
      marketHashId = marketHash.hashCode,
      addressShardId = MultiAccountManagerActor
        .getEntityId(owner, 100)
        .toInt
    )
    dbModule.orderService.saveOrder(order)
  }

  private def testSaves(
      hashes: Set[String],
      owner: String,
      status: XOrderStatus,
      tokenS: String,
      tokenB: String,
      validSince: Int,
      validUntil: Int
    ): Future[Set[Either[XRawOrder, XErrorCode]]] = {
    for {
      result ← Future.sequence(hashes.map { hash ⇒
        testSave(hash, owner, status, tokenS, tokenB, validSince, validUntil)
      })
    } yield result
  }

  "recover an address" must {
    "get all effective orders and recover" in {
      val owner = "0xb7e0dae0a3e4e146bcaf0fe782be5afb14041a10"
      // 1. select depth
      val getOrderBook1 = XGetOrderbook(
        0,
        100,
        Some(XMarketId(LRC_TOKEN.address, WETH_TOKEN.address))
      )
      val orderbookF1 = singleRequest(
        getOrderBook1,
        "orderbook"
      )
      val timeout1 = Timeout(5 second)
      val orderbookRes1 = Await.result(orderbookF1, timeout1.duration)
      println(1111, orderbookRes1)

      // 2. save some orders in db
      val state0 = Set(
        "0x-recover-state0-01",
        "0x-recover-state0-02",
        "0x-recover-state0-03",
        "0x-recover-state0-04",
        "0x-recover-state0-05"
      )
      testSaves(
        state0,
        owner,
        XOrderStatus.STATUS_NEW,
        LRC_TOKEN.address,
        WETH_TOKEN.address,
        validSince,
        validUntil.toInt
      )
      val state1 = Set(
        "0x-recover-state1-01",
        "0x-recover-state1-02"
      )
      testSaves(
        state1,
        owner,
        XOrderStatus.STATUS_PENDING,
        LRC_TOKEN.address,
        WETH_TOKEN.address,
        validSince,
        validUntil.toInt
      )
      val state2 = Set(
        "0x-recover-state2-01",
        "0x-recover-state2-02"
      )
      testSaves(
        state2,
        owner,
        XOrderStatus.STATUS_EXPIRED,
        "0x021",
        "0x022",
        validSince,
        validUntil.toInt
      )
      val state3 = Set(
        "0x-recover-state3-01",
        "0x-recover-state3-02",
        "0x-recover-state3-03",
        "0x-recover-state3-04",
        "0x-recover-state3-05"
      )
      testSaves(
        state3,
        owner,
        XOrderStatus.STATUS_DUST_ORDER,
        "0x031",
        "0x032",
        validSince,
        validUntil.toInt
      )
      val state4 = Set(
        "0x-recover-state4-01",
        "0x-recover-state4-02",
        "0x-recover-state4-03"
      )
      testSaves(
        state4,
        owner,
        XOrderStatus.STATUS_PARTIALLY_FILLED,
        "0x041",
        "0x042",
        validSince,
        validUntil.toInt
      )
      // 3. recover
      val request1 = XRecover.Request(
        addressShardingEntity = MultiAccountManagerActor
          .getEntityId(owner, 100)
      )
      implicit val timeout = Timeout(100 second)
      val r = actors.get(OrderRecoverCoordinator.name) ? request1
      val res = Await.result(r, timeout.duration)
      res match {
        case XRecover.Finished(b) => assert(b)
        case _                    => assert(false)
      }
      // 4. get depth
      val orderbookRes2 = Await.result(orderbookF1, timeout1.duration)
      println(22222, orderbookRes1)
    }
  }

}
