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

package org.loopring.lightcone.actors.ethereum.processor

import akka.actor.ActorRef
import org.loopring.lightcone.actors.base.Lookup
import org.loopring.lightcone.actors.ethereum._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.ethereum.event.BalanceChangedAddressExtractor
import org.loopring.lightcone.proto._
import org.loopring.lightcone.actors.base.safefuture._
import akka.pattern._
import org.web3j.utils.Numeric
import scala.concurrent.ExecutionContext
import org.loopring.lightcone.actors.data._

class BalanceChangedAddressExtractorWrapped(
  )(
    implicit ec: ExecutionContext,
    actors: Lookup[ActorRef],
    brb: EthereumBatchCallRequestBuilder)
    extends DataExtractorWrapped[AddressBalanceUpdated] {

  val ethereumAccessor = actors.get(EthereumAccessActor.name)

  val extractor = new BalanceChangedAddressExtractor()
  override def extractData(blockJob: BlockJob): Seq[AddressBalanceUpdated] = {
    val addresses = super.extractData(blockJob)
    val miners = blockJob.uncles +: blockJob.miner
    events = addresses ++ miners.map { miner =>
      AddressBalanceUpdated(
        address = miner.toString,
        token = Address.ZERO.toString()
      )
    }
    events
  }

  override def process(): Unit = {
    val (ethAddresses, tokenAddresses) =
      events.partition(addr => Address(addr.token).isZero)
    val batchCallReq = brb.buildRequest(tokenAddresses, tag = "")
    for {
      tokenBalances <- (ethereumAccessor ? batchCallReq)
        .mapAs[BatchCallContracts.Res]
        .map(_.resps.map(_.result))
        .map(_.map(res => Numeric.toBigInt(res).toByteArray))
      ethBalances <- (ethereumAccessor ? BatchGetEthBalance.Req(
        reqs =
          ethAddresses.map(addr => EthGetBalance.Req(address = addr.address))
      )).mapAs[BatchGetEthBalance.Res]
        .map(_.resps.map(_.result))
        .map(_.map(res => Numeric.toBigInt(res).toByteArray))
    } yield {
      val tokenUpdatedAddresses = (tokenAddresses zip tokenBalances).map(
        item => item._1.withBalance(item._2)
      )
      val ethUpdatedAddresses =
        (ethAddresses zip ethBalances).map(item => item._1.withBalance(item._2))
      val updatedAddresses = tokenUpdatedAddresses ++ ethUpdatedAddresses
      processors.foreach(
        processor => updatedAddresses.foreach(addr => processor.process(addr))
      )
    }
  }
}
