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

package io.lightcone.relayer.socketio

import io.lightcone.relayer.actors.SocketIONotificationActor
import io.lightcone.relayer.data.{GetAccount, SubmitOrder}
import io.lightcone.relayer.support._
import akka.pattern._
import io.lightcone.relayer.base._

import scala.concurrent.Await
import scala.math.BigInt

class SocketSpec extends CommonSpec with EventExtractorSupport {

  "socket server  test" must {
    "socket server starts normally and can subscriber and received correct data" in {

      def socketNotifier = actors.get(SocketIONotificationActor.name)
      Thread.sleep(10 * 1000)

//      socketNotifier ! TokenMetadata(
//        address = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc",
//        websiteUrl = "https"
//      )
//      Thread.sleep(2 * 1000)
//
//      socketNotifier ! TokenMetadata(
//        address = "0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc",
//        websiteUrl = "http"
//      )
      val submit_order = "submit_order"
      val order1 = createRawOrder()
      val account0 = accounts.head
      val account1 = getUniqueAccountWithoutEth

      val method = "get_account"
      val getBalanceReq =
        GetAccount.Req(
          account0.getAddress,
          tokens = Seq(LRC_TOKEN.name, WETH_TOKEN.address)
        )
      Await.result(singleRequest(getBalanceReq, method), timeout.duration)

      val submitOrder1F =
        singleRequest(SubmitOrder.Req(Some(order1)), submit_order)
          .mapAs[SubmitOrder.Res]
      Await.result(submitOrder1F, timeout.duration)

      Await.result(
        transferEth(account1.getAddress, "10")(account0),
        timeout.duration
      )

      Await.result(
        transferWETH(account1.getAddress, "20")(account0),
        timeout.duration
      )

      Await.result(
        approveWETHToDelegate("10000")(account1),
        timeout.duration
      )

      val order2 = createRawOrder(
        tokenS = WETH_TOKEN.address,
        tokenB = LRC_TOKEN.address,
        amountS = "1".zeros(18),
        amountB = "10".zeros(18)
      )(account1)

      val submitOrder2F =
        singleRequest(SubmitOrder.Req(Some(order2)), submit_order)
          .mapAs[SubmitOrder.Res]
      Await.result(submitOrder2F, timeout.duration)

      Thread.sleep(15 * 1000)
    }
  }
}
