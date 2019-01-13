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

// /*
//  * Copyright 2018 Loopring Foundation
//  *
//  * Licensed under the Apache License, Version 2.0 (the "License");
//  * you may not use this file except in compliance with the License.
//  * You may obtain a copy of the License at
//  *
//  *     http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing, software
//  * distributed under the License is distributed on an "AS IS" BASIS,
//  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  * See the License for the specific language governing permissions and
//  * limitations under the License.
//  */

// package org.loopring.lightcone.actors.ethereum

// import akka.actor.ActorRef
// import com.typesafe.config.Config
// import org.loopring.lightcone.actors.base.Lookup
// import org.loopring.lightcone.ethereum.event._
// import org.loopring.lightcone.proto._
// import akka.util.Timeout
// import org.loopring.lightcone.actors.core._
// import org.loopring.lightcone.actors.utils.TokenMetadataRefresher

// import scala.concurrent.ExecutionContext

// object EventDispatchers {

//   def getDefaultDispatchers(
//       implicit
//       actors: Lookup[ActorRef],
//       config: Config,
//       brb: EthereumBatchCallRequestBuilder,
//       timeout: Timeout,
//       ec: ExecutionContext
//     ): Seq[EventDispatcher[_]] = {

//     implicit val cutoffExtractor = new CutoffEventExtractor
//     implicit val ordersCancelledExtractor = new OrdersCancelledEventExtractor
//     implicit val tokenBurnRateExtractor = new TokenBurnRateEventExtractor
//     implicit val transferExtractor = new TransferEventExtractor
//     implicit val ringMinedExtractor = new RingMinedEventExtractor
//     implicit val addressAllowanceUpdatedExtractor =
//       new AllowanceChangedAddressExtractor
//     implicit val balanceChangedAddressExtractor =
//       new BalanceChangedAddressExtractor

//     var dispatchers = Seq.empty[EventDispatcher[_]]

//     dispatchers +:=
//       new NameBasedEventDispatcher[CutoffEvent](
//         Seq(
//           TransactionRecordActor.name,
//           OrderCutoffHandlerActor.name,
//           MultiAccountManagerActor.name
//         )
//       )

//     dispatchers +:=
//       new NameBasedEventDispatcher[OrdersCancelledEvent](
//         Seq(TransactionRecordActor.name, OrderCutoffHandlerActor.name)
//       )

//     dispatchers +:=
//       new NameBasedEventDispatcher[TokenBurnRateChangedEvent](
//         Seq(TokenMetadataRefresher.name)
//       )

//     dispatchers +:=
//       new NameBasedEventDispatcher[TransferEvent](
//         Seq(TransactionRecordActor.name)
//       ) {
//         def derive(event: TransferEvent) =
//           Seq(event.withOwner(event.from), event.withOwner(event.to))
//       }

//     dispatchers +:=
//       new NameBasedEventDispatcher[RingMinedEvent](
//         Seq(TransactionRecordActor.name, MultiAccountManagerActor.name)
//       ) {
//         def derive(event: RingMinedEvent) = event.fills
//       }

//     dispatchers +:=
//       new NameBasedEventDispatcher[RingMinedEvent](Seq(MarketManagerActor.name))

//     dispatchers +:=
//       new BalanceEventDispatcher(
//         Seq(MultiAccountManagerActor.name, RingSettlementManagerActor.name)
//       )

//     dispatchers +:=
//       new AllowanceEventDispatcher(Seq(MultiAccountManagerActor.name))

//     dispatchers
//   }
// }
