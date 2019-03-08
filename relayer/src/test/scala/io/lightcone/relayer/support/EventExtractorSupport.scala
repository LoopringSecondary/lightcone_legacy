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
import io.lightcone.ethereum.event._
import io.lightcone.ethereum.extractor._
import io.lightcone.ethereum.extractor.block.{
  AllowanceUpdateAddressExtractor,
  BalanceUpdateAddressExtractor
}
import io.lightcone.ethereum.extractor.tx._
import io.lightcone.ethereum.persistence.OHLCRawData
import io.lightcone.relayer.actors._
import io.lightcone.relayer.data.BlockWithTxObject
import io.lightcone.relayer.ethereum.{
  EthereumAccessActor,
  EventDispatcher,
  EventDispatcherImpl
}

trait EventExtractorSupport
    extends DatabaseModuleSupport
    with EthereumSupport
    with MetadataManagerSupport
    with JsonrpcSupport
    with HttpSupport
    with OrderHandleSupport
    with MultiAccountManagerSupport
    with MarketManagerSupport
    with OrderbookManagerSupport
    with OrderGenerateSupport
    with RingAndFillPersistenceSupport
    with DatabaseQueryMessageSupport {

  com: CommonSpec =>

  implicit val eventDispatcher: EventDispatcher =
    new EventDispatcherImpl(actors)
      .register(
        classOf[RingMinedEvent],
        MarketManagerActor.name,
        RingAndFillPersistenceActor.name
      )
      .register(classOf[CutoffEvent], MultiAccountManagerActor.name)
      .register(classOf[OrderFilledEvent], MultiAccountManagerActor.name)
      .register(
        classOf[OrdersCancelledOnChainEvent],
        MultiAccountManagerActor.name
      )
      .register(
        classOf[TokenBurnRateChangedEvent], //
        MetadataManagerActor.name
      )
      .register(
        classOf[OHLCRawData], //
        MarketHistoryActor.name
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

  implicit val txEventExtractor: EventExtractor[TransactionData, AnyRef] =
    EventExtractor.compose[TransactionData, AnyRef]( //
      new TxCutoffEventExtractor(),
      new TxRingMinedEventExtractor(),
      new TxTokenBurnRateEventExtractor(),
      new TxTransferEventExtractor(),
      new TxApprovalEventExtractor()
    )

  val ethereumAccess = () => actors.get(EthereumAccessActor.name)
  val approvalEventExtractor = new TxApprovalEventExtractor()
  val txTransferEventExtractor = new TxTransferEventExtractor()

  implicit val blockEventExtractor: EventExtractor[BlockWithTxObject, AnyRef] =
    new DefaultEventExtractor(
      EventExtractor.compose[BlockWithTxObject, AnyRef](
        new BlockGasPriceExtractor(),
        new AllowanceUpdateAddressExtractor(
          ethereumAccess,
          approvalEventExtractor
        ),
        new BalanceUpdateAddressExtractor(
          ethereumAccess,
          txTransferEventExtractor
        )
      )
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
