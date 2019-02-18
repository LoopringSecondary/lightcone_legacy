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

package io.lightcone.relayer.ethereum

import akka.actor.ActorRef
import com.google.inject.Inject
import io.lightcone.ethereum.event._
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum.event.EventExtractor
import scala.concurrent.ExecutionContext

object Dispatchers {

  class RingMinedEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      ec: ExecutionContext,
      extractor: EventExtractor[RingMinedEvent])
      extends NameBasedEventDispatcher[RingMinedEvent](
        names = Seq(MarketManagerActor.name, RingAndTradePersistenceActor.name),
        actors
      )

  class AllowanceEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[AddressAllowanceUpdatedEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressAllowanceUpdatedEvent](
        names = Seq(MultiAccountManagerActor.name),
        actors
      )

  class BalanceEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[AddressBalanceUpdatedEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressBalanceUpdatedEvent](
        names =
          Seq(MultiAccountManagerActor.name, RingSettlementManagerActor.name),
        actors
      )

  class CutoffEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[CutoffEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[CutoffEvent](
        names = Seq(TransactionRecordActor.name, MultiAccountManagerActor.name),
        actors
      )

  class OrderFilledEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[OrderFilledEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[OrderFilledEvent](
        names = Seq(TransactionRecordActor.name, MultiAccountManagerActor.name),
        actors
      )

  class OrdersCancelledOnChainEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[OrdersCancelledOnChainEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[OrdersCancelledOnChainEvent](
        names = Seq(TransactionRecordActor.name, MultiAccountManagerActor.name),
        actors
      )

  class TokenBurnRateChangedEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[TokenBurnRateChangedEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[TokenBurnRateChangedEvent](
        names = Seq(MetadataManagerActor.name),
        actors
      )

  class TransferEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[TransferEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[TransferEvent](
        names = Seq(TransactionRecordActor.name),
        actors
      )

  class OHLCRawDataEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[OHLCRawDataEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[OHLCRawDataEvent](
        names = Seq(OHLCDataHandlerActor.name),
        actors
      )

  class BlockGasPricesExtractedEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[BlockGasPricesExtractedEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[BlockGasPricesExtractedEvent](
        names = Seq(GasPriceActor.name),
        actors
      )
}
