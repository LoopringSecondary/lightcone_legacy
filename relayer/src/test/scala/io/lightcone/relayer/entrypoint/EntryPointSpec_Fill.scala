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

import io.lightcone.core.{MarketHash, MarketPair}
import io.lightcone.relayer.data.GetFillHistory
import io.lightcone.relayer.support._
import scala.concurrent.Await
import io.lightcone.ethereum.persistence.Fill

class EntryPointSpec_Fill
    extends CommonSpec
    with DatabaseModuleSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with OrderGenerateSupport
    with DatabaseQueryMessageSupport
    with RingAndFillPersistenceSupport {

  val tokenS1 = "0x87b95e3aefeb28d8a32a46e8c5278721dad39550"
  val tokenB1 = "0x9862c9413f2cc21ebfda534ecfa6df4f59f0b197"
  val tokenS2 = "0x27b95e3aefeb28d8a32a46e8c5278721dad39550"
  val tokenB2 = "0x2862c9413f2cc21ebfda534ecfa6df4f59f0b197"

  val txHash1 =
    "0x116331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"

  val txHash2 =
    "0x216331920f91aa6f40e10c3e6c87e6d58aec01acb6e9a244983881d69bc0cff4"

  "save and query fills" must {
    "save some fills" in {
      Seq(
        (
          "0x151df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
          tokenS1,
          tokenB1,
          txHash1
        ),
        (
          "0x251df14e49da86abc6f1d8ccc0b3a6b7b7c90ca6",
          tokenS2,
          tokenB2,
          txHash2
        )
      ).map { d =>
        saveFills(d._1, d._2, d._3, d._4)
      }
    }

    "query fill history without filter market" in {
      val f1 = singleRequest(GetFillHistory.Req(None), "get_fill_history")
      val res1 = Await.result(f1.mapTo[GetFillHistory.Res], timeout.duration)
      res1.fills.length should be(2)
    }

    "query fill history with filter not exist market" in {
      val f1 = singleRequest(
        GetFillHistory.Req(
          Some(
            MarketPair(
              "0x7189ff502fb784c49202507f5e41a7fb4a313721",
              "0x4c4efa19df14d57bd85b0bfc9af528019479a3d2"
            )
          )
        ),
        "get_fill_history"
      )
      val res1 = Await.result(f1.mapTo[GetFillHistory.Res], timeout.duration)
      res1.fills.length should be(0)
    }

    "query fill history with filter market" in {
      val f1 = singleRequest(
        GetFillHistory.Req(Some(MarketPair(tokenB1, tokenS1))),
        "get_fill_history"
      )
      val res1 = Await.result(f1.mapTo[GetFillHistory.Res], timeout.duration)
      res1.fills.length should be(1)
    }

    "query fill history with filter market in different side" in {
      val f1 = singleRequest(
        GetFillHistory.Req(Some(MarketPair(tokenS1, tokenB1))),
        "get_fill_history"
      )
      val res1 = Await.result(f1.mapTo[GetFillHistory.Res], timeout.duration)
      res1.fills.length should be(1)
    }
  }

  private def saveFills(
      owner: String,
      tokenS: String,
      tokenB: String,
      txHash: String
    ) = {
    val fill = Fill(
      owner = owner,
      tokenB = tokenB,
      tokenS = tokenS,
      marketHash = MarketHash(MarketPair(tokenS, tokenB)).hashString(),
      txHash = txHash
    )
    dbModule.fillDal.saveFill(fill)
    dbModule.fillDal.saveFill(
      fill.copy(tokenB = tokenS, tokenS = tokenB, isTaker = true, fillIndex = 1)
    )
  }

}
