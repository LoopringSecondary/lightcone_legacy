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

package io.lightcone.relayer.jsonrpc

import io.lightcone.relayer.support._

import io.lightcone.core._
import io.lightcone.proto._

import scala.concurrent.Await

class JrpcSpec
    extends CommonSpec
    with EthereumSupport
    with MetadataManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with JsonrpcSupport
    with HttpSupport {

  "send serval JRPC requests" must {
    "return correct responses" in {
      // 正确返回
      val resonse1 = singleRequest(
        GetOrderbook
          .Req(0, 2, Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address))),
        "get_orderbook"
      )
      // 只要返回了Orderbook类型就认为成功，其他会抛异常
      Await.result(resonse1.mapTo[GetOrderbook.Res], timeout.duration)

      // 1. 没有在EntryPoint绑定过的request消息类型;
      //    错误的request类型 => 反序列化为默认的proto对象，不支持进入validator
      // 2. 错误的validate请求
      val resonse2 =
        singleRequest(GetOrderbook.Req(0, 2, None), "get_orderbook")
      val result2 = try {
        Await.result(resonse2, timeout.duration)
      } catch {
        // ErrorException(ERR_UNEXPECTED_ACTOR_MSG:
        // unexpected msg of io.lightcone.proto.GetOrderbook$Req)
        case e: ErrorException => e
      }
      result2 match {
        case e: ErrorException
            if e.error.code === ErrorCode.ERR_INTERNAL_UNKNOWN && e.error.message
              .contains("ERR_UNEXPECTED_ACTOR_MSG") =>
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
