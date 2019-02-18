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

import io.lightcone.relayer.actors._
import io.lightcone.relayer.ethereum.Dispatchers._
import io.lightcone.relayer.ethereum.event._
import io.lightcone.ethereum._

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

  implicit val orderValidator: RawOrderValidator = new RawOrderValidatorImpl

  implicit val balanceExtractor = new BalanceChangedAddressExtractor
  implicit val allowanceExtractor = new AllowanceChangedAddressExtractor
  implicit val cutoffExtractor = new CutoffEventExtractor
  implicit val ordersCancelledExtractor =
    new OrdersCancelledOnChainEventExtractor
  implicit val tokenBurnRateExtractor = new TokenBurnRateEventExtractor
  implicit val transferExtractor = new TransferEventExtractor
  implicit val ringMinedExtractor = new RingMinedEventExtractor
  implicit val orderFillEventExtractor = new OrderFillEventExtractor
  implicit val ohlcRawDataExtractor = new OHLCRawDataExtractor
  implicit val gasPricesExtractor = new BlockGasPriceExtractor

  implicit val dispatchers = Seq(
    new CutoffEventDispatcher(actors),
    new OrdersCancelledOnChainEventDispatcher(actors),
    new TokenBurnRateChangedEventDispatcher(actors),
    new TransferEventDispatcher(actors),
    new OrderFilledEventDispatcher(actors),
    new RingMinedEventDispatcher(actors),
    new BalanceEventDispatcher(actors),
    new AllowanceEventDispatcher(actors),
    new OHLCRawDataEventDispatcher(actors),
    new BlockGasPricesExtractedEventDispatcher(actors)
  )

  actors.add(
    EthereumEventExtractorActor.name,
    EthereumEventExtractorActor.start
  )
  actors.add(
    MissingBlocksEventExtractorActor.name,
    MissingBlocksEventExtractorActor.start
  )
  actors.add(OHLCDataHandlerActor.name, OHLCDataHandlerActor.start)
}
