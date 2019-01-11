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
import akka.util.Timeout
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.data.byteArray2ByteString
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event.EventExtractor
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import akka.pattern._

import scala.concurrent.ExecutionContext

class AllowanceEventDispatcher(
    names: Seq[String]
  )(
    implicit
    extractor: EventExtractor[AddressAllowanceUpdated],
    lookup: Lookup[ActorRef],
    config: Config,
    brb: EthereumBatchCallRequestBuilder,
    timeout: Timeout,
    ec: ExecutionContext)
    extends NameBasedEventDispatcher[
      AddressAllowanceUpdated,
      AddressAllowanceUpdated
    ](names)
    with NonDerivable[AddressAllowanceUpdated, AddressAllowanceUpdated] {

  override def extractThenDispatchEvents(block: RawBlockData) = {
    val delegateAddress = Address(
      config.getString("loopring_protocol.delegate-address")
    )
    val events = (block.txs zip block.receipts).flatMap { item =>
      extractor.extract(item._1, item._2, block.timestamp)
    }
    val batchCallReq = brb.buildRequest(delegateAddress, events, "")
    for {
      tokenAllowances <- (lookup.get(EthereumAccessActor.name) ? batchCallReq)
        .mapAs[BatchCallContracts.Res]
        .map(_.resps.map(_.result))
        .map(_.map(res => Numeric.toBigInt(res).toByteArray))
    } yield {
      (events zip tokenAllowances).foreach(
        item =>
          targets
            .foreach(_ ! item._1.withBalance(byteArray2ByteString(item._2)))
      )
    }
  }
}
