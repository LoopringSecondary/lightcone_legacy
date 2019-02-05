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

package org.loopring.lightcone.persistence.dals

import org.loopring.lightcone.core._
import org.loopring.lightcone.proto._
import scala.concurrent.Await
import scala.concurrent.duration._

class MarketMetadataDalSpec extends DalSpec[MarketMetadataDal] {

  import ErrorCode._

  def getDal = new MarketMetadataDalImpl()

  "save markets config" must "save some markets config" in {
    info("save 3 market configs")
    val LRC = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
    val BNB = "0x9b9211a2ce4eEE9c5619d54E5CD9f967A68FBE23"
    val ZRX = "0x7b22713f2e818fad945af5a3618a2814f102cbe0"
    val WETH = "0x45245bc59219eeaaf6cd3f382e078a461ff9de7b"

    val marketPairLrcWeth = MarketPair(LRC, WETH)
    val marketPairBnbWeth = MarketPair(BNB, WETH)
    val marketPairZrxdWeth = MarketPair(ZRX, WETH)
    val markets = Seq(
      MarketMetadata(
        status = MarketMetadata.Status.ACTIVE,
        baseTokenSymbol = "LRC",
        quoteTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPairLrcWeth),
        marketHash = MarketHash(MarketPair(WETH, LRC)).toString),
      MarketMetadata(
        status = MarketMetadata.Status.TERMINATED,
        baseTokenSymbol = "BNB",
        quoteTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPairBnbWeth),
        marketHash = MarketHash(MarketPair(WETH, BNB)).toString),
      MarketMetadata(
        status = MarketMetadata.Status.READONLY,
        baseTokenSymbol = "ZRX",
        quoteTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketPair = Some(marketPairZrxdWeth),
        marketHash = MarketHash(MarketPair(ZRX, WETH)).toString))
    val r1 = dal.saveMarkets(markets)
    val res1 = Await.result(r1.mapTo[Seq[String]], 5.second)
    assert(res1.length == markets.length)

    info("query the markets config just saved")
    val r2 = dal.getMarketsByKey(markets.map(_.marketHash))
    val res2 = Await.result(r2.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res2.length == markets.length)
    res2 foreach {
      case m: MarketMetadata if m.baseTokenSymbol == "LRC" =>
        assert(
          m.status == MarketMetadata.Status.ACTIVE
            && m.quoteTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketPair.contains(marketPairLrcWeth)
            && m.marketHash == MarketHash(MarketPair(LRC, WETH)).toString)
      case m: MarketMetadata if m.baseTokenSymbol == "BNB" =>
        assert(
          m.status == MarketMetadata.Status.TERMINATED
            && m.quoteTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketPair.contains(marketPairBnbWeth)
            && m.marketHash == MarketHash(MarketPair(BNB, WETH)).toString)
      case m: MarketMetadata if m.baseTokenSymbol == "ZRX" =>
        assert(
          m.status == MarketMetadata.Status.READONLY
            && m.quoteTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketPair.contains(marketPairZrxdWeth)
            && m.marketHash == MarketHash(MarketPair(ZRX, WETH)).toString)
      case _ => assert(false)
    }
    val lrcWeth =
      res2.find(_.baseTokenSymbol == "LRC").getOrElse(MarketMetadata())

    info("duplicate market save should return error")
    val market1 = lrcWeth.copy(priceDecimals = 10)
    val r3 = dal.saveMarket(market1)
    val res3 = Await.result(r3.mapTo[ErrorCode], 5.second)
    assert(res3 == ERR_PERSISTENCE_DUPLICATE_INSERT)
    val r4 = dal.getMarketsByKey(Seq(lrcWeth.marketHash))
    val res4 = Await.result(r4.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res4.length == 1)
    val lrcWeth1 = res4.find(_.baseTokenSymbol == "LRC")
    assert(lrcWeth1.nonEmpty && lrcWeth1.get.priceDecimals == 8)

    info(
      "should not save market with too long address :0xBe4C1cb10C2Be76798c4186ADbbC34356b358b521")
    val r5 = dal.saveMarket(
      lrcWeth.copy(
        marketPair =
          Some(MarketPair("0xBe4C1cb10C2Be76798c4186ADbbC34356b358b521", WETH))))
    val res5 = Await.result(r5.mapTo[ErrorCode], 5.second)
    assert(res5 == ERR_PERSISTENCE_INTERNAL)
    val r6 = dal.getMarkets()
    val res6 = Await.result(r6.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res6.nonEmpty && res6.length == markets.length)

    info(
      "update BNB's status, maxNumbersOfOrders, priceDecimals, orderbookAggLevels, precisionForAmount, precisionForTotal, browsableInWallet")
    val bnbWeth =
      res2.find(_.baseTokenSymbol == "BNB").getOrElse(MarketMetadata())
    val r9 = dal.updateMarket(
      bnbWeth.copy(
        status = MarketMetadata.Status.ACTIVE,
        maxNumbersOfOrders = 2000,
        priceDecimals = 3,
        orderbookAggLevels = 2,
        precisionForAmount = 22,
        precisionForTotal = 33,
        browsableInWallet = false))
    val res9 = Await.result(r9.mapTo[ErrorCode], 5.second)
    assert(res9 == ERR_NONE)
    val r10 = dal.getMarketsByKey(Seq(bnbWeth.marketHash))
    val res10 = Await.result(r10.mapTo[Seq[MarketMetadata]], 5.second)
    val bnb1 =
      res10.find(_.baseTokenSymbol == "BNB").getOrElse(MarketMetadata())
    assert(
      bnb1.status == MarketMetadata.Status.ACTIVE
        && bnb1.quoteTokenSymbol == "WETH"
        && bnb1.maxNumbersOfOrders == 2000
        && bnb1.priceDecimals == 3
        && bnb1.orderbookAggLevels == 2
        && bnb1.precisionForAmount == 22
        && bnb1.precisionForTotal == 33
        && !bnb1.browsableInWallet
        && bnb1.marketPair.contains(marketPairBnbWeth)
        && bnb1.marketHash == MarketHash(MarketPair(BNB, WETH)).toString)
  }
}
