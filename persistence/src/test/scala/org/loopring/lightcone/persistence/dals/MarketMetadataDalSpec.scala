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

import org.loopring.lightcone.lib.MarketHashProvider
import org.loopring.lightcone.proto.ErrorCode._
import org.loopring.lightcone.proto._
import scala.concurrent.Await
import scala.concurrent.duration._

class MarketMetadataDalSpec extends DalSpec[MarketMetadataDal] {
  def getDal = new MarketMetadataDalImpl()

  "save markets config" must "save some markets config" in {
    info("save 3 market configs")
    val LRC = "0xf51df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6"
    val BNB = "0x9b9211a2ce4eEE9c5619d54E5CD9f967A68FBE23"
    val ZRX = "0x7b22713f2e818fad945af5a3618a2814f102cbe0"
    val WETH = "0x45245bc59219eeaaf6cd3f382e078a461ff9de7b"

    val marketIdLrcWeth = MarketId(primary = WETH, secondary = LRC)
    val marketIdBnbWeth = MarketId(primary = WETH, secondary = BNB)
    val marketIdZrxdWeth = MarketId(primary = WETH, secondary = ZRX)
    val markets = Seq(
      MarketMetadata(
        status = MarketMetadata.Status.ENABLED,
        secondaryTokenSymbol = "LRC",
        primaryTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdLrcWeth),
        marketHash = MarketHashProvider.convert2Hex(WETH, LRC)
      ),
      MarketMetadata(
        status = MarketMetadata.Status.DISABLED,
        secondaryTokenSymbol = "BNB",
        primaryTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdBnbWeth),
        marketHash = MarketHashProvider.convert2Hex(WETH, BNB)
      ),
      MarketMetadata(
        status = MarketMetadata.Status.READONLY,
        secondaryTokenSymbol = "ZRX",
        primaryTokenSymbol = "WETH",
        maxNumbersOfOrders = 1000,
        priceDecimals = 8,
        orderbookAggLevels = 1,
        precisionForAmount = 11,
        precisionForTotal = 12,
        browsableInWallet = true,
        updatedAt = timeProvider.getTimeMillis,
        marketId = Some(marketIdZrxdWeth),
        marketHash = MarketHashProvider.convert2Hex(WETH, ZRX)
      )
    )
    val r1 = dal.saveMarkets(markets)
    val res1 = Await.result(r1.mapTo[Seq[String]], 5.second)
    assert(res1.length == markets.length)

    info("query the markets config just saved")
    val r2 = dal.getMarketsByHashes(markets.map(_.marketHash))
    val res2 = Await.result(r2.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res2.length == markets.length)
    res2 foreach {
      case m: MarketMetadata if m.secondaryTokenSymbol == "LRC" =>
        assert(
          m.status == MarketMetadata.Status.ENABLED
            && m.primaryTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketId.contains(marketIdLrcWeth)
            && m.marketHash == MarketHashProvider.convert2Hex(WETH, LRC)
        )
      case m: MarketMetadata if m.secondaryTokenSymbol == "BNB" =>
        assert(
          m.status == MarketMetadata.Status.DISABLED
            && m.primaryTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketId.contains(marketIdBnbWeth)
            && m.marketHash == MarketHashProvider.convert2Hex(WETH, BNB)
        )
      case m: MarketMetadata if m.secondaryTokenSymbol == "ZRX" =>
        assert(
          m.status == MarketMetadata.Status.READONLY
            && m.primaryTokenSymbol == "WETH"
            && m.maxNumbersOfOrders == 1000
            && m.priceDecimals == 8
            && m.orderbookAggLevels == 1
            && m.precisionForAmount == 11
            && m.precisionForTotal == 12
            && m.browsableInWallet
            && m.marketId.contains(marketIdZrxdWeth)
            && m.marketHash == MarketHashProvider.convert2Hex(WETH, ZRX)
        )
      case _ => assert(false)
    }
    val lrcWeth =
      res2.find(_.secondaryTokenSymbol == "LRC").getOrElse(MarketMetadata())

    info("duplicate market save should return error")
    val market1 = lrcWeth.copy(priceDecimals = 10)
    val r3 = dal.saveMarket(market1)
    val res3 = Await.result(r3.mapTo[ErrorCode], 5.second)
    assert(res3 == ErrorCode.ERR_PERSISTENCE_DUPLICATE_INSERT)
    val r4 = dal.getMarketsByHashes(Seq(lrcWeth.marketHash))
    val res4 = Await.result(r4.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res4.length == 1)
    val lrcWeth1 = res4.find(_.secondaryTokenSymbol == "LRC")
    assert(lrcWeth1.nonEmpty && lrcWeth1.get.priceDecimals == 8)

    info(
      "should not save market with too long address :0xBe4C1cb10C2Be76798c4186ADbbC34356b358b521"
    )
    val r5 = dal.saveMarket(
      lrcWeth.copy(
        marketId = Some(
          MarketId(
            primary = WETH,
            secondary = "0xBe4C1cb10C2Be76798c4186ADbbC34356b358b521"
          )
        )
      )
    )
    val res5 = Await.result(r5.mapTo[ErrorCode], 5.second)
    assert(res5 == ErrorCode.ERR_PERSISTENCE_INTERNAL)
    val r6 = dal.getMarkets()
    val res6 = Await.result(r6.mapTo[Seq[MarketMetadata]], 5.second)
    assert(res6.nonEmpty && res6.length == markets.length)

    info(
      "update BNB's status, maxNumbersOfOrders, priceDecimals, orderbookAggLevels, precisionForAmount, precisionForTotal, browsableInWallet"
    )
    val bnbWeth =
      res2.find(_.secondaryTokenSymbol == "BNB").getOrElse(MarketMetadata())
    val r9 = dal.updateMarket(
      bnbWeth.copy(
        status = MarketMetadata.Status.ENABLED,
        maxNumbersOfOrders = 2000,
        priceDecimals = 3,
        orderbookAggLevels = 2,
        precisionForAmount = 22,
        precisionForTotal = 33,
        browsableInWallet = false
      )
    )
    val res9 = Await.result(r9.mapTo[ErrorCode], 5.second)
    assert(res9 == ErrorCode.ERR_NONE)
    val r10 = dal.getMarketsByHashes(Seq(bnbWeth.marketHash))
    val res10 = Await.result(r10.mapTo[Seq[MarketMetadata]], 5.second)
    val bnb1 =
      res10.find(_.secondaryTokenSymbol == "BNB").getOrElse(MarketMetadata())
    assert(
      bnb1.status == MarketMetadata.Status.ENABLED
        && bnb1.primaryTokenSymbol == "WETH"
        && bnb1.maxNumbersOfOrders == 2000
        && bnb1.priceDecimals == 3
        && bnb1.orderbookAggLevels == 2
        && bnb1.precisionForAmount == 22
        && bnb1.precisionForTotal == 33
        && !bnb1.browsableInWallet
        && bnb1.marketId.contains(marketIdBnbWeth)
        && bnb1.marketHash == MarketHashProvider.convert2Hex(WETH, BNB)
    )
  }
}
