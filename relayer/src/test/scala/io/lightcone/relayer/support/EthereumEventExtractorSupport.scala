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

package io.lightcone.relayer.support

import io.lightcone.ethereum.{RawOrderValidator, RawOrderValidatorImpl}
import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum.event._
import io.lightcone.ethereum.event._
import io.lightcone.relayer.ethereum.{EventDispatcher, EventDispatcherActorImpl}

trait EthereumEventExtractorSupport
    extends DatabaseModuleSupport
    with EthereumSupport
    with MetadataManagerSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with DatabaseQueryMessageSupport
    with RingAndTradePersistenceSupport
    with EthereumTransactionRecordSupport {
  me: CommonSpec =>

  actors.add(OHLCDataHandlerActor.name, OHLCDataHandlerActor.start)

  implicit val orderValidator: RawOrderValidator = new RawOrderValidatorImpl

  implicit val extractors: Seq[EventExtractor] = Seq(
    new BalanceAndAllowanceChangedExtractor(),
    new BlockGasPriceExtractor(),
    new CutoffEventExtractor(),
    new OnchainOrderExtractor(),
    new OrdersCancelledEventExtractor(),
    new RingMinedEventExtractor(),
    new TokenBurnRateEventExtractor()
  )

  implicit val eventExtractorCompose = new EventExtractorCompose()
  implicit val eventDispatcher = new EventDispatcherActorImpl()

  eventDispatcher.register(
    RingMinedEvent().getClass,
    actors.get(MarketManagerActor.name),
    actors.get(RingAndTradePersistenceActor.name)
  )

  eventDispatcher.register(
    CutoffEvent().getClass,
    actors.get(TransactionRecordActor.name),
    actors.get(MultiAccountManagerActor.name)
  )

  eventDispatcher.register(
    OrderFilledEvent().getClass,
    actors.get(TransactionRecordActor.name),
    actors.get(MultiAccountManagerActor.name)
  )

  eventDispatcher.register(
    OrdersCancelledOnChainEvent().getClass,
    actors.get(TransactionRecordActor.name),
    actors.get(MultiAccountManagerActor.name)
  )

  eventDispatcher.register(
    TokenBurnRateChangedEvent().getClass,
    actors.get(MetadataManagerActor.name)
  )

  eventDispatcher.register(
    TransferEvent().getClass,
    actors.get(TransactionRecordActor.name)
  )

  eventDispatcher.register(
    OHLCRawDataEvent().getClass,
    actors.get(OHLCDataHandlerActor.name)
  )

  eventDispatcher.register(
    BlockGasPricesExtractedEvent().getClass,
    actors.get(GasPriceActor.name)
  )
  eventDispatcher.register(
    AddressAllowanceUpdatedEvent().getClass,
    actors.get(MultiAccountManagerActor.name)
  )
  eventDispatcher.register(
    AddressBalanceUpdatedEvent().getClass,
    actors.get(MultiAccountManagerActor.name),
    actors.get(RingSettlementManagerActor.name)
  )

  actors.add(
    EthereumEventExtractorActor.name,
    EthereumEventExtractorActor.start
  )
//  actors.add(
//    MissingBlocksEventExtractorActor.name,
//    MissingBlocksEventExtractorActor.start
//  )
}
