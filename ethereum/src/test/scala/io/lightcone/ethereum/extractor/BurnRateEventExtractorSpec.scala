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

import io.lightcone.ethereum.RawOrderValidatorImpl
import io.lightcone.ethereum.abi._

import scala.concurrent.Await
import scala.concurrent.duration._

class BurnRateEventExtractorSpec extends AbstractExtractorSpec {

  "extract a block contains BurnRateEvent" should "get events correctly" in {
    import scala.concurrent.ExecutionContext.Implicits.global

    implicit val orderValidator = new RawOrderValidatorImpl()
    val burnRateEventExtractor = new TxTokenBurnRateEventExtractor()
    //TODO(hongyu):燃烧的流程：将lrc授权给BurnRateTable，然后调用update生成TokenTierUpgradedEvent事件
    val transactions =
      getTransactionDatas("ethereum/src/test/resources/event/ring_mined_block")
    val tx = transactions(0)
    loopringProtocolAbi.unpackEvent(
      tx.receiptAndHeaderOpt.get._1.logs(0).data,
      tx.receiptAndHeaderOpt.get._1.logs(0).topics.toArray
    )
    val events = Await.result(
      burnRateEventExtractor.extractEvents(tx),
      5.second
    )

  }
}
