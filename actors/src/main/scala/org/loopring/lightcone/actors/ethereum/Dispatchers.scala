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

package org.loopring.lightcone.actors.ethereum

import akka.actor.ActorRef
import com.google.inject.Inject
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.core._
import org.loopring.lightcone.actors.ethereum.event.EventExtractor
import org.loopring.lightcone.actors.utils.TokenMetadataRefresher
import org.loopring.lightcone.proto._
import scala.concurrent.ExecutionContext

object Dispatchers {

  class RingMinedEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val ec: ExecutionContext,
      val extractor: EventExtractor[RingMinedEvent])
      extends NameBasedEventDispatcher[RingMinedEvent](
        names = Seq(MarketManagerActor.name)
      )

  class AllowanceEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[AddressAllowanceUpdated],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressAllowanceUpdated](
        names = Seq(MultiAccountManagerActor.name)
      )

  class BalanceEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[AddressBalanceUpdated],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressBalanceUpdated](
        names =
          Seq(MultiAccountManagerActor.name, RingSettlementManagerActor.name)
      )

  class CutoffEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[CutoffEvent],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[CutoffEvent](
        names = Seq(
          TransactionRecordActor.name,
          OrderCutoffHandlerActor.name,
          MultiAccountManagerActor.name
        )
      )

  class OrderFilledEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[RingMinedEvent],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[RingMinedEvent](
        names = Seq(TransactionRecordActor.name, MultiAccountManagerActor.name)
      )

  class OrdersCancelledEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[OrdersCancelledEvent],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[OrdersCancelledEvent](
        names = Seq(TransactionRecordActor.name, OrderCutoffHandlerActor.name)
      )

  class TokenBurnRateChangedEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[TokenBurnRateChangedEvent],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[TokenBurnRateChangedEvent](
        names = Seq(TokenMetadataRefresher.name)
      )

  class TransferEventDispatcher @Inject()(
      implicit
      val lookup: Lookup[ActorRef],
      val extractor: EventExtractor[TransferEvent],
      val ec: ExecutionContext)
      extends NameBasedEventDispatcher[TransferEvent](
        names = Seq(TransactionRecordActor.name)
      )

}
