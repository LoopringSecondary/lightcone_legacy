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
import io.lightcone.relayer.base.Lookup
import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum.event.EventExtractor
import io.lightcone.relayer.data._
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
      extractor: EventExtractor[AddressAllowanceUpdated],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressAllowanceUpdated](
        names = Seq(MultiAccountManagerActor.name),
        actors
      )

  class BalanceEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[AddressBalanceUpdated],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[AddressBalanceUpdated](
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
        names = Seq(
          TransactionRecordActor.name,
          MultiAccountManagerActor.name
        ),
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

  class OrdersCancelledEventDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[OrdersCancelledEvent],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[OrdersCancelledEvent](
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
      extractor: EventExtractor[OHLCRawData],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[OHLCRawData](
        names = Seq(OHLCDataHandlerActor.name),
        actors
      )

  class BlockGasPricesDispatcher @Inject()(
      actors: Lookup[ActorRef]
    )(
      implicit
      extractor: EventExtractor[BlockGasPrices],
      ec: ExecutionContext)
      extends NameBasedEventDispatcher[BlockGasPrices](
        names = Seq(GasPriceActor.name),
        actors
      )
}
