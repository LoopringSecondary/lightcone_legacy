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

package org.loopring.lightcone.actors.jsonrpc

import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.support._
import org.loopring.lightcone.lib.ErrorException
import org.loopring.lightcone.proto
import org.loopring.lightcone.proto._
import scala.concurrent.Await

class JrpcSpec
    extends CommonSpec
    with EthereumSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  override def beforeAll() {
    info(s">>>>>> To run this spec, use `testOnly *${getClass.getSimpleName}`")
  }

  "send serval JRPC requests" must {
    "return correct responses" in {
      // 正确返回
      val resonse1 = singleRequest(
        GetOrderbook.Req(
          0,
          2,
          Some(
            MarketId(
              "0x1B56AC0087e5CB7624A04A80b1c28B60A30f28D1",
              "0x8B75225571ff31B58F95C704E05044D5CF6B32BF"
            )
          )
        ),
        "orderbook"
      )
      // 只要返回了Orderbook类型就认为成功，其他会抛异常
      Await.result(resonse1.mapTo[GetOrderbook.Res], timeout.duration)

      // 1. 没有在EntryPoint绑定过的request消息类型; 错误的request类型 => 反序列化为默认的proto对象，进入validator
      // 2. 错误的validate请求
      val resonse2 = singleRequest(GetOrderbook.Req(0, 2, None), "orderbook")
      val result2 = try {
        Await.result(resonse2, timeout.duration)
      } catch {
        // ErrorException(ERR_INTERNAL_UNKNOWN: msg:JsonRpcError(3010,Some(),None))
        case e: ErrorException => e
      }
      result2 match {
        case e: ErrorException
            if e.error.code === ErrorCode.ERR_INTERNAL_UNKNOWN && e.error.message
              .contains("3010") =>
          assert(true)
        case _ =>
          assert(false)
      }

      // 调用没有注册过的actor
      val resonse3 = singleRequest(
        GetBalanceAndAllowances.Req(
          "0xb94065482ad64d4c2b9252358d746b39e820a582",
          tokens = Seq(LRC_TOKEN.address, WETH_TOKEN.address)
        ),
        "get_balance_and_allowance"
      )
      val result3 = try {
        Await.result(resonse3, timeout.duration)
      } catch {
        //  ErrorException(ERR_INTERNAL_UNKNOWN: msg:JsonRpcError(1,Some(not found actor: multi_account_manager_validator),None))
        case e: ErrorException => e
      }
      result3 match {
        case e: ErrorException
            if e.error.code === ErrorCode.ERR_INTERNAL_UNKNOWN && e.error.message
              .contains("not found actor") =>
          assert(true)
        case _ => assert(false)
      }

      // 调用没有注册过的method
      val resonse4 =
        singleRequest(GetOrderbook.Req(0, 2, None), "method-not-exist")
      val result4 = try {
        Await.result(resonse4, timeout.duration)
      } catch {
        // ErrorException(ERR_INTERNAL_UNKNOWN: msg:JsonRpcError(-32601,None,None))
        case e: ErrorException => e
      }
      result4 match {
        case e: ErrorException
            if e.error.code === ErrorCode.ERR_INTERNAL_UNKNOWN && e.error.message
              .contains("-32601") =>
          assert(true)
        case _ =>
          assert(false)
      }
    }
  }
}
