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

package io.lightcone.relayer.actors

import akka.pattern._
import akka.testkit.TestProbe
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.support._
import io.lightcone.relayer.data._
import io.lightcone.core._
import scala.concurrent.{Await, Future}

class ProcessEthereumSpec_RingMinedEvent
    extends CommonSpec
    with JsonrpcSupport
    with HttpSupport
    with EthereumSupport
    with MetadataManagerSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport {

  val ringSettlementProbe =
    new TestProbe(system, RingSettlementManagerActor.name)

  //为便于手动发送RingMinedEvent，重置RingSettlementManagerActor，防止环路被提交
  actors.del(RingSettlementManagerActor.name)
  actors.add(RingSettlementManagerActor.name, ringSettlementProbe.ref)

  val account1 = getUniqueAccountWithoutEth
  //设置余额
  info("set the balance and allowance is enough befor submit an order")
  override def beforeAll(): Unit = {
    val f = Future.sequence(
      Seq(
        transferEth(account1.getAddress, "10")(accounts(0)),
        transferLRC(account1.getAddress, "60")(accounts(0)),
        approveLRCToDelegate("60")(account1)
      )
    )

    Await.result(f, timeout.duration)
    super.beforeAll()
  }

  "MarketMangerActor receive a RingMined Event" must {
    "delete ring state in PendingPool" in {
      info("submit a ring")
      val account0 = accounts(0)
      val order1 = createRawOrder()(account1)

      val order0 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = order1.amountS,
        amountS = order1.amountB
      )(account0)

      val submitOrderF1 = Future.sequence(
        Seq(
          singleRequest(SubmitOrder.Req(Some(order0)), "submit_order"),
          singleRequest(SubmitOrder.Req(Some(order1)), "submit_order")
        )
      )
      ringSettlementProbe.expectMsgPF() {
        case msg: SettleRings =>
          info(s"received a msg of SettleRings:${msg}")
      }

      info("send a RingMinedEvent that it's txStatus is success ")
      val successEvent = RingMinedEvent(
        header = Some(EventHeader(txStatus = TxStatus.TX_STATUS_SUCCESS)),
        orderIds = Seq(order0.hash, order1.hash)
      )
      val eventF1 = actors.get(MarketManagerActor.name) ? successEvent

      Await.result(eventF1, timeout.duration)

      //but how to confirm??
      info("submit a new ring")
      val validUntil = timeProvider.getTimeSeconds().toInt + 20001
      val order3 = createRawOrder(validUntil = validUntil)(account1)

      val order4 = createRawOrder(
        tokenB = LRC_TOKEN.address,
        tokenS = WETH_TOKEN.address,
        amountB = order3.amountS,
        amountS = order3.amountB,
        validUntil = validUntil
      )(account0)

      val submitOrderF2 = Future.sequence(
        Seq(
          singleRequest(SubmitOrder.Req(Some(order3)), "submit_order"),
          singleRequest(SubmitOrder.Req(Some(order4)), "submit_order")
        )
      )
      ringSettlementProbe.expectMsgPF() {
        case msg: SettleRings =>
          info(s"received a msg of SettleRings:${msg}")
      }

      info("send a RingMinedEvent that it's txStatus is failed.")
      val failedEvent = RingMinedEvent(
        header = Some(EventHeader(txStatus = TxStatus.TX_STATUS_FAILED)),
        orderIds = Seq(order3.hash, order4.hash)
      )
      val eventF2 = actors.get(MarketManagerActor.name) ? failedEvent
      Await.result(eventF2, timeout.duration)

      info("the ring should be submitted again.")
      var receivedAgain = false
      ringSettlementProbe.expectMsgPF() {
        case msg: SettleRings =>
          receivedAgain = true
          info(s"received a msg of SettleRings:${msg}")
      }
      assert(receivedAgain)

    }
  }

}
