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

import akka.pattern._
import akka.testkit.TestProbe
import io.lightcone.core.OrderStatus._
import io.lightcone.ethereum._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data._
import io.lightcone.relayer.support._

import scala.concurrent.Await

class EntryPointSpec_CancelOrderAfterManyRingFailures
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

  //屏蔽RingSettlementManagerActor 防止被提交到以太坊并执行成功
  val probe = TestProbe()
  actors.del(RingSettlementManagerActor.name)
  actors.add(RingSettlementManagerActor.name, probe.ref)

  "submit an order" must {
    "be canceled after many ring failures" in {

      info("submit an order.")
      val order1 =
        createRawOrder(
          amountS = "10".zeros(LRC_TOKEN.decimals),
          tokenS = LRC_TOKEN.address,
          amountB = "1".zeros(WETH_TOKEN.decimals),
          tokenB = WETH_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )

      Await.result(
        singleRequest(SubmitOrder.Req(Some(order1)), "submit_order"),
        timeout.duration
      )

      info(
        "submit several orders that can be matched and then send a failed RingMinedEvent."
      )

      val maxSettementFailuresPerOrder =
        config.getInt("market_manager.max-ring-failures-per-order") + 1

      val order2 =
        createRawOrder(
          amountS = "1".zeros(WETH_TOKEN.decimals) / 2,
          tokenS = WETH_TOKEN.address,
          amountB = "10".zeros(LRC_TOKEN.decimals) / 2,
          tokenB = LRC_TOKEN.address,
          amountFee = "10".zeros(LRC_TOKEN.decimals),
          tokenFee = LRC_TOKEN.address
        )
      Await.result(
        singleRequest(SubmitOrder.Req(Some(order2)), "submit_order"),
        timeout.duration
      )
      (0 until maxSettementFailuresPerOrder).map { i =>
        Thread.sleep(500)
        val f = actors.get(MarketManagerActor.name) ? RingMinedEvent(
          header = Some(EventHeader(txStatus = TxStatus.TX_STATUS_FAILED)),
          fills = Seq(
            OrderFilledEvent(
              orderHash = order2.hash,
              tokenS = order2.tokenS,
              tokenB = order2.tokenB
            ),
            OrderFilledEvent(
              orderHash = order1.hash,
              tokenS = order1.tokenS,
              tokenB = order1.tokenB
            )
          )
        )
        Await.result(
          f,
          timeout.duration
        )
      }

      Thread.sleep(1000)
      info("the first order should be canceled.")
      val getOrderF = dbModule.orderService.getOrder(order1.hash)

      val orderOpt = Await.result(getOrderF, timeout.duration)
      orderOpt.nonEmpty should be(true)
      orderOpt.get.getState.status should be(
        STATUS_SOFT_CANCELLED_TOO_MANY_RING_FAILURES
      )
    }
  }

}
