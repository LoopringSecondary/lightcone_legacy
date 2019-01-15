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

import com.google.inject.Inject
import akka.actor.ActorRef
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event.EventExtractor
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import akka.pattern._
import org.loopring.lightcone.actors.core.MultiAccountManagerActor

import scala.concurrent.{ExecutionContext, Future}

class AllowanceEventDispatcher @Inject()(
    implicit
    config: Config,
    brb: EthereumBatchCallRequestBuilder,
    timeout: Timeout,
    val extractor: EventExtractor[AddressAllowanceUpdated],
    val lookup: Lookup[ActorRef],
    val ec: ExecutionContext)
    extends NameBasedEventDispatcher[AddressAllowanceUpdated] {

  val names: Seq[String] = Seq(MultiAccountManagerActor.name)

  val delegateAddress = Address(
    config.getString("loopring_protocol.delegate-address")
  )
  def ethereumAccessor = lookup.get(EthereumAccessActor.name)

  // override def derive(
  //     block: RawBlockData
  //   ): Future[Seq[AddressAllowanceUpdated]] = {
  //   val items = block.txs zip block.receipts
  //   val events: Seq[AddressAllowanceUpdated] = items.flatMap { item =>
  //     extractor.extract(item._1, item._2, block.timestamp)
  //   }.distinct
  //   val batchCallReq = brb.buildRequest(delegateAddress, events, "latest")
  //   for {
  //     tokenAllowances <- (ethereumAccessor ? batchCallReq)
  //       .mapAs[BatchCallContracts.Res]
  //       .map(_.resps.map(_.result))
  //       .map(_.map(res => Numeric.toBigInt(res).toByteArray))
  //   } yield {
  //     (events zip tokenAllowances).map(item => item._1.withBalance(item._2))
  //   }
  // }
}
