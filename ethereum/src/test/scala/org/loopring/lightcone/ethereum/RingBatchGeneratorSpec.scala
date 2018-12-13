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

import com.typesafe.config.ConfigFactory
import com.google.protobuf.ByteString

import org.loopring.lightcone.proto._

class MyConfig(val fileNameOption: Option[String] = None) {

  val config = fileNameOption.fold(
    ifEmpty = ConfigFactory.load()
  )(
      file ⇒ ConfigFactory.load(file)
    )

  def envOrElseConfig(name: String): String = {
    Properties.envOrElse(
      name.toUpperCase.replaceAll("""\.""", "_"),
      config.getString(name)
    )
  }
}

class RingBatchGeneratorSpec extends FlatSpec with Matchers {
  val ethereumConf = new MyConfig(Some("ethereum.conf"))

  println(s"ethereumConf: $ethereumConf")

  "simple 2 tradable orders" should "be able to generate a ring" in {
    val lrcAddress = ethereumConf.envOrElseConfig("contracts.LRC")
    val wethAddress = ethereumConf.envOrElseConfig("contracts.WETH")
    val gtoAddress = ethereumConf.envOrElseConfig("contracts.GTO")

    val order1Owner = ethereumConf.envOrElseConfig("accounts.a1.addr")
    val order2Owner = ethereumConf.envOrElseConfig("accounts.a2.addr")

    val miner = ethereumConf.envOrElseConfig("accounts.a3.addr")
    val minerPrivKey = ethereumConf.envOrElseConfig("accounts.a3.privKey")

    // println(s"lrcAddress: $lrcAddress, $wethAddress, $miner, $minerPrivKey")

    val xRingBatchContext = (new XRingBatchContext).withFeeRecipient(miner)
      .withMiner(miner)
      .withTransactionOrigin(miner)
      .withMinerPrivateKey(minerPrivKey)
      .withLrcAddress(lrcAddress)

    println(s"xRingBatchContext: $xRingBatchContext")

    val ringBatchGenerator = new RingBatchGeneratorImpl(xRingBatchContext)

    val order1 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order1Owner)
      .withTokenS(lrcAddress)
      .withTokenB(wethAddress)
      .withAmountS(ByteString.copyFromUtf8(1000e18.toLong.toHexString))
      .withAmountB(ByteString.copyFromUtf8(1e18.toLong.toHexString))

    val order2 = (new XRawOrder)
      .withVersion(0)
      .withOwner(order2Owner)
      .withTokenS(wethAddress)
      .withTokenB(lrcAddress)
      .withAmountS(ByteString.copyFromUtf8(1e18.toLong.toHexString))
      .withAmountB(ByteString.copyFromUtf8(1000e18.toLong.toHexString))

    val orders = Seq(Seq(order1, order2))
    val xRingBatch = ringBatchGenerator.generateAndSignRingBatch(orders)
    println(s"xRingBatch: $xRingBatch")

    val param = ringBatchGenerator.toSubmitableParamStr(xRingBatch)
    println(s"param str: $param")

  }
}
