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
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event.EventExtractor
import org.loopring.lightcone.actors.base.safefuture._
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import akka.pattern._

import scala.concurrent.ExecutionContext

class BalanceEventDispatcher(
    implicit
    extractor: EventExtractor[AddressBalanceUpdated],
    lookup: Lookup[ActorRef],
    brb: EthereumBatchCallRequestBuilder,
    timeout: Timeout,
    val ec: ExecutionContext)
    extends NameBasedEventDispatcher[AddressBalanceUpdated] {

  val names: Seq[String] = ???

  //TODO(yadong): 不要从新定义dispatch方法，而是改写derive方法。

  // override def dispatch(block: RawBlockData) = {

  //   val miners: Seq[String] = block.uncles.+:(block.miner)
  //   val events = ((block.txs zip block.receipts).flatMap { item =>
  //     extractor.extract(item._1, item._2, block.timestamp)
  //   } ++ miners.map(
  //     miner =>
  //       AddressBalanceUpdated(address = miner, token = Address.ZERO.toString())
  //   )).distinct
  //   val (ethAddress, tokenAddresses) =
  //     events.partition(addr => Address(addr.address).isZero)
  //   val batchCallReq = brb.buildRequest(tokenAddresses, "latest")
  //   for {
  //     tokenBalances <- (lookup.get(EthereumAccessActor.name) ? batchCallReq)
  //       .mapAs[BatchCallContracts.Res]
  //       .map(_.resps.map(_.result))
  //       .map(_.map(res => Numeric.toBigInt(res).toByteArray))
  //     ethBalances <- (lookup.get(EthereumAccessActor.name) ? BatchGetEthBalance
  //       .Req(
  //         reqs =
  //           ethAddress.map(addr => EthGetBalance.Req(address = addr.address))
  //       ))
  //       .mapAs[BatchGetEthBalance.Res]
  //       .map(_.resps.map(_.result))
  //       .map(_.map(res => Numeric.toBigInt(res).toByteArray))
  //   } yield {
  //     (tokenAddresses zip tokenBalances).foreach(
  //       item =>
  //         targets
  //           .foreach(_ ! item._1.withBalance(item._2))
  //     )
  //     (ethAddress zip ethBalances).foreach(
  //       item =>
  //         targets
  //           .foreach(_ ! item._1.withBalance(item._2))
  //     )
  //   }
  // }
}
