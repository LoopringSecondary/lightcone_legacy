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

package org.loopring.lightcone.actors.entrypoint

import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.ethereum.getSignedTxData
import org.loopring.lightcone.ethereum.data.{Address, Transaction}
import org.loopring.lightcone.proto._
import org.scalatest.WordSpec
import org.web3j.crypto.Credentials
import org.web3j.utils.Numeric
import akka.pattern._

import scala.concurrent.{Await, Future}

class SendTransaction extends CommonSpec with EthereumSupport {

  "send an orderbook request" must {
    "receive a response without value" in {

      val ethereumAccessorActor = actors.get(EthereumAccessActor.name)
//      val f = (ethereumAccessorActor ? EthGetBalance.Req(
//        address = accounts(1).getAddress,
//        tag = "latest"
//      ))
//
//      val r = Await.result(f.mapTo[EthGetBalance.Res], timeout.duration)
//      info(s"${r.result}, ${r.error}")

//      val f2 =
//        transferEth(accounts(1).getAddress, BigInt("100000000"))(accounts(0))
//
//      val f3 = (ethereumAccessorActor ? EthGetBalance.Req(
//        address = accounts(1).getAddress,
//        tag = "latest"
//      ))
//
//      val r3 = Await.result(f3.mapTo[EthGetBalance.Res], timeout.duration)
//      info(s"${r3.result}, ${r3.error}")

      val f = Future.sequence(
        Seq(
          transferErc20(
            accounts(1).getAddress,
            LRC_TOKEN.address,
            "30".zeros(LRC_TOKEN.decimals)
          )(accounts(0)),
          approveErc20(
            "0xCa66Ffaf17e4B600563f6af032456AA7B05a6975",
            LRC_TOKEN.address,
            "30".zeros(LRC_TOKEN.decimals)
          )(accounts(1))
        )
      )
      //todo: allowance 为0 的逻辑是什么，accountmanager与marketmanager中是否需要保存
      Await.result(f, timeout.duration)

      val data = erc20Abi.balanceOf.pack(
        BalanceOfFunction.Parms(
          _owner = accounts(1).getAddress
        )
      )

      val param = TransactionParams(
        to = "0x97241525fe425C90eBe5A41127816dcFA5954b06",
        data = data
      )

      val f1 = (ethereumAccessorActor ? EthCall.Req(1, Some(param), "latest"))

      val r1 = Await.result(f1.mapTo[EthCall.Res], timeout.duration)
      info(s"${r1.result}, ${r1.error}")

    }
  }
}
