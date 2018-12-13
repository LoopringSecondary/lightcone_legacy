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

package org.loopring.lightcone.ethereum.data

import scala.util.Properties
import org.scalatest._
import com.google.protobuf.ByteString
import org.loopring.lightcone.proto._

class RawOrderValidatorSpec extends FlatSpec with Matchers {
  val validator: RawOrderValidator = new RawOrderValidatorImpl

  "user" should "be able to get hash of an order" in {
    val lrcAddress = TestConfig.envOrElseConfig("contracts.LRC")
    val wethAddress = TestConfig.envOrElseConfig("contracts.WETH")
    val gtoAddress = TestConfig.envOrElseConfig("contracts.GTO")

    val order1Owner = TestConfig.envOrElseConfig("accounts.a1.addr")
    val order2Owner = TestConfig.envOrElseConfig("accounts.a2.addr")

    val order1 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFromUtf8(1000e18.toLong.toHexString))
      .withAmountB(ByteString.copyFromUtf8(1e18.toLong.toHexString))
    println(s"order1: $order1")

    val order1WithDefault = validator.setupEmptyFieldsWithDefaults(order1, lrcAddress)
    println(s"order1WithDefault: $order1WithDefault")

    val hash = validator.calculateOrderHash(order1WithDefault)
    println(s"hash:$hash")

    val validateResult = validator.validate(order1WithDefault)
    println(s"validateResult: $validateResult")
  }
}
