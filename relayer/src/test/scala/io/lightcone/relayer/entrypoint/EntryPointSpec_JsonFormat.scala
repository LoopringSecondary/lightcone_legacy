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

import com.google.protobuf.ByteString
import io.lightcone.core.RawOrder1.FeeParams
import io.lightcone.core._
import io.lightcone.lib.NumericConversion
import org.json4s.JsonAST.JString
import org.scalatest.WordSpec
import scalapb.json4s._

class EntryPointSpec_JsonFormat extends WordSpec {

  "send an orderbook request" must {
    "receive a response without value" in {
      val formatRegistry =
        JsonFormat.DefaultRegistry
          .registerWriter[io.lightcone.core.Amount](
            (amount: io.lightcone.core.Amount) =>
              JString(
                NumericConversion.toHexString(BigInt(amount.value.toByteArray))
              ), {
              case JString(str) =>
                io.lightcone.core.Amount(
                  value = ByteString
                    .copyFrom(NumericConversion.toBigInt(str).toByteArray)
                )
              case _ => throw new JsonFormatException("Expected a string.")
            }
          )
      val order = RawOrder1(
        tokenS = "0xaaaa",
        amountS = BigInt(10000),
        amountA = Some(Amount(ByteString.copyFrom(BigInt(10000).toByteArray))),
        feeParams = Some(
          FeeParams(
            amountFee =
              Some(Amount(ByteString.copyFrom(BigInt(500).toByteArray))),
            tokenFee = "0xbbbb"
          )
        )
      )
      val orderStr = new Printer(formatRegistry = formatRegistry).print(order)
      info(s"order's jsonString: ${orderStr}")
      val order1 = new Parser(formatRegistry = formatRegistry)
        .fromJsonString[RawOrder1](orderStr)
      info(
        s"order1: ${order1},\n " +
          s"order1.amountS:${BigInt(order1.amountS.toByteArray)},\n " +
          s"order1.amountA: ${BigInt(order1.getAmountA.value.toByteArray)}, \n" +
          s"order1.amountFee: ${BigInt(order1.getFeeParams.getAmountFee.value.toByteArray)},\n " +
          s"order1.tokenFee: ${order1.getFeeParams.tokenFee}"
      )
    }
  }

}
