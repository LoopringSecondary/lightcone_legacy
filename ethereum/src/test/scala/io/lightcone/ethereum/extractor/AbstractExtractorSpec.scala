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

package io.lightcone.ethereum.extractor
import com.typesafe.config.ConfigFactory
import io.lightcone.core.{
  MarketHash,
  MarketMetadata,
  MarketPair,
  MetadataManagerImpl,
  TokenMetadata
}
import io.lightcone.lib.{Address, ProtoSerializer}
import org.json4s.DefaultFormats
import org.scalatest.{FlatSpec, Matchers}

abstract class AbstractExtractorSpec extends FlatSpec with Matchers {

  implicit val config =
    ConfigFactory.parseResources("ethereum_protocol.conf")
  implicit val metadataManager = new MetadataManagerImpl(0.3, 0.3)

  val WETH_TOKEN = TokenMetadata(
    address = Address("0x7Cb592d18d0c49751bA5fce76C1aEc5bDD8941Fc").toString,
    decimals = 18,
    burnRateForMarket = 0.4,
    burnRateForP2P = 0.5,
    symbol = "WETH",
    name = "WETH",
    status = TokenMetadata.Status.VALID
  )

  val LRC_TOKEN = TokenMetadata(
    address = Address("0x97241525fe425C90eBe5A41127816dcFA5954b06").toString,
    decimals = 18,
    burnRateForMarket = 0.4,
    burnRateForP2P = 0.5,
    symbol = "LRC",
    name = "LRC",
    status = TokenMetadata.Status.VALID
  )

  val LRC_WETH_MARKET = MarketMetadata(
    status = MarketMetadata.Status.ACTIVE,
    baseTokenSymbol = LRC_TOKEN.symbol,
    quoteTokenSymbol = WETH_TOKEN.symbol,
    maxNumbersOfOrders = 1000,
    priceDecimals = 6,
    orderbookAggLevels = 6,
    precisionForAmount = 5,
    precisionForTotal = 5,
    browsableInWallet = true,
    marketPair = Some(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)),
    marketHash =
      MarketHash(MarketPair(LRC_TOKEN.address, WETH_TOKEN.address)).toString
  )

  metadataManager.reset(
    Seq(LRC_TOKEN, WETH_TOKEN),
    Seq.empty,
    Map.empty,
    Seq(LRC_WETH_MARKET)
  )

  implicit val formats = DefaultFormats

  implicit val protocolAddress =
    Address.normalize("0xB7101ff647ac42e776bA857907DdBE743522AA95")
  implicit val delegateAddress =
    Address.normalize("0xCa66Ffaf17e4B600563f6af032456AA7B05a6975")

  val ps = new ProtoSerializer

  val parser = org.json4s.native.JsonParser

  def deserializeToProto[T <: scalapb.GeneratedMessage with scalapb.Message[T]](
      json: String
    )(
      implicit
      pa: scalapb.GeneratedMessageCompanion[T]
    ): Option[T] = {
    val jv = parser.parse(json)
    ps.deserialize[T](jv)
  }

}
