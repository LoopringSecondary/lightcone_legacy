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

import io.lightcone.relayer.support._

class SocketSpec
    extends CommonSpec
    with EthereumEventExtractorSupport
    with SocketSupport {

  "socket server  test" must {
    "socket server starts normally and can subscriber and received correct data" in {

      //      def socketListener = actors.get(SocketIONotificationActor.name)
      //      Thread.sleep(10 * 1000)
      //      val account0 = accounts.head
      //      val account1 = getUniqueAccountWithoutEth
      //      val getBaMethod = "get_balance_and_allowance"
      //      singleRequest(
      //        GetBalanceAndAllowances.Req(
      //          account0.getAddress,
      //          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
      //        ),
      //        getBaMethod
      //      ).mapAs[GetBalanceAndAllowances.Res].foreach(res => socketListener ! res)
      //      Thread.sleep(2000)
      //      transferWETH(account1.getAddress, "100")(account0)
      //      Thread.sleep(3000)
      //      singleRequest(
      //        GetBalanceAndAllowances.Req(
      //          account0.getAddress,
      //          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
      //        ),
      //        getBaMethod
      //      ).mapAs[GetBalanceAndAllowances.Res].foreach(res => socketListener ! res)
      //
      //      Thread.sleep(Int.MaxValue)
    }
  }
}
