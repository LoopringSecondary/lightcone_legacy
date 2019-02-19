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
import io.lightcone.relayer.ethereum._

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
  implicit val eventExtractor: EventExtractor = new EventExtractorCompose()

  implicit val eventDispatcher: EventDispatcher =
    new EventDispatcherActorImpl(actors)
      .register(
        classOf[RingMinedEvent],
        MarketManagerActor.name,
        RingAndTradePersistenceActor.name
      )
      .register(
        classOf[CutoffEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[OrderFilledEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[OrdersCancelledOnChainEvent],
        TransactionRecordActor.name,
        MultiAccountManagerActor.name
      )
      .register(
        classOf[TokenBurnRateChangedEvent], //
        MetadataManagerActor.name
      )
      .register(
        classOf[TransferEvent], //
        TransactionRecordActor.name
      )
      .register(
        classOf[OHLCRawDataEvent], //
        OHLCDataHandlerActor.name
      )
      .register(
        classOf[BlockGasPricesExtractedEvent], //
        GasPriceActor.name
      )
      .register(
        classOf[AddressAllowanceUpdatedEvent],
        MultiAccountManagerActor.name
      )
      .register(
        classOf[AddressBalanceUpdatedEvent],
        MultiAccountManagerActor.name,
        RingSettlementManagerActor.name
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
