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

package org.loopring.lightcone.actors.support

import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum.Dispatchers._
import org.loopring.lightcone.actors.ethereum.event._

trait EthereumEventExtractorSupport {
  my: CommonSpec with EthereumSupport with DatabaseModuleSupport =>

  implicit val balanceExtractor = new BalanceChangedAddressExtractor
  implicit val allowanceExtractor = new AllowanceChangedAddressExtractor
  implicit val cutoffExtractor = new CutoffEventExtractor
  implicit val ordersCancelledExtractor = new OrdersCancelledEventExtractor
  implicit val tokenBurnRateExtractor = new TokenBurnRateEventExtractor
  implicit val transferExtractor = new TransferEventExtractor
  implicit val ringMinedExtractor = new RingMinedEventExtractor
  implicit val orderFillEventExtractor = new OrderFillEventExtractor

  implicit val dispatchers = Seq(
    new CutoffEventDispatcher(actors),
    new OrdersCancelledEventDispatcher(actors),
    new TokenBurnRateChangedEventDispatcher(actors),
    new TransferEventDispatcher(actors),
    new OrderFilledEventDispatcher(actors),
    new RingMinedEventDispatcher(actors),
    new BalanceEventDispatcher(actors),
    new AllowanceEventDispatcher(actors)
  )

  actors.add(
    EthereumEventExtractorActor.name,
    EthereumEventExtractorActor.start
  )
  actors.add(
    MissingBlocksEventExtractorActor.name,
    MissingBlocksEventExtractorActor.start
  )
}
