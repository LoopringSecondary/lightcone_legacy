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

package org.loopring.lightcone.ethereum.event.processor

import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event.BalanceChangedAddressExtractor
import org.loopring.lightcone.proto.{AddressBalanceUpdated, BlockJob}

class BalanceChangedAddressExtractorWrapped()
    extends DataExtractorWrapped[AddressBalanceUpdated] {
  val extractor = new BalanceChangedAddressExtractor()
  override def extractData(blockJob: BlockJob): Seq[AddressBalanceUpdated] = {
    val addresses = super.extractData(blockJob)
    val miners = blockJob.uncles +: blockJob.miner
    events = addresses ++ miners.map { miner =>
      AddressBalanceUpdated(
        address = miner.toString,
        token = Address.ZERO.toString()
      )
    }
    events
  }
}
