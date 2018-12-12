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

package org.loopring.lightcone.actors.core

import akka.actor._
import akka.cluster.sharding._
import akka.event.LoggingReceive
import akka.pattern._
import akka.util.Timeout
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import org.loopring.lightcone.actors.base._
import org.loopring.lightcone.actors.data._
import org.loopring.lightcone.actors.ethereum.EthereumAccessActor
import org.loopring.lightcone.ethereum.abi._
import org.loopring.lightcone.ethereum.data.Address
import org.loopring.lightcone.lib.TimeProvider
import org.loopring.lightcone.proto._
import org.web3j.utils.Numeric
import org.loopring.lightcone.actors.ethereum._

import scala.concurrent.{ ExecutionContext, Future }

object EthereumQueryActor extends ShardedEvenly {
  val name = "ethereum_query"

  def startShardRegion()(
    implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef]
  ): ActorRef = {

    val selfConfig = config.getConfig(name)
    numOfShards = selfConfig.getInt("num-of-shards")
    entitiesPerShard = selfConfig.getInt("entities-per-shard")

    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new EthereumQueryActor()),
      settings = ClusterShardingSettings(system).withRole(name),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class EthereumQueryActor()(
    implicit
    val config: Config,
    val ec: ExecutionContext,
    val timeProvider: TimeProvider,
    val timeout: Timeout,
    val actors: Lookup[ActorRef]
) extends ActorWithPathBasedConfig(EthereumQueryActor.name) {

  val delegateAddress = config.getString("loopring-protocol.delegate-address")
  val erc20Abi = ERC20ABI()
  val zeroAddress: String = "0x" + "0" * 40

  protected def ethereumConnectionActor = actors.get(EthereumAccessActor.name)

  //todo:还需要继续优化下
  def receive = LoggingReceive {
    case req: XGetBalanceAndAllowancesReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceAndAllowancesReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceAndAllowancesRes()
          .withAddress(req.address)
          .withError(XError()
            .withCode(XErrorCode.ETHEREUM_ERR_ILLEGAL_ADDRESS)
            .withMessage(s"invalid address in XGetBalanceAndAllowancesReq:$req"))
      } else {
        val batchReqs: XBatchContractCallReq = xGetBalanceAndAllowanceToBatchReq(Address(delegateAddress), req)
        val existsEth = req.tokens.exists(token ⇒ Address(token).toString.equals(zeroAddress))
        (for {
          callRes ← (ethereumConnectionActor ? batchReqs).mapTo[XBatchContractCallRes]
          ethRes ← (ethereumConnectionActor ? XEthGetBalanceReq(address = Address(req.address).toString, tag = "latest"))
            .mapTo[XEthGetBalanceRes]
          res: XGetBalanceAndAllowancesRes = xBatchContractCallResToBalanceAndAllowance(req.address, req.tokens, callRes)
        } yield {
          res.copy(
            balanceAndAllowanceMap = res.balanceAndAllowanceMap +
              (zeroAddress → XBalanceAndAllowance(BigInt(Numeric.toBigInt(ethRes.result)), BigInt(0)))
          )
        }) pipeTo sender
      }

    case req: XGetBalanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceRes()
          .withAddress(req.address)
          .withError(XError()
            .withCode(XErrorCode.ETHEREUM_ERR_ILLEGAL_ADDRESS)
            .withMessage(s"invalid address in XGetBalanceAndAllowancesReq:$req"))
      } else {
        val batchReqs: XBatchContractCallReq = req
        val existsEth = req.tokens.exists(token ⇒ Address(token).toString.equals(zeroAddress))
        (for {
          callRes ← (ethereumConnectionActor ? batchReqs).mapTo[XBatchContractCallRes]
          ethRes ← (ethereumConnectionActor ? XEthGetBalanceReq(address = Address(req.address).toString, tag = "latest"))
            .mapTo[XEthGetBalanceRes]
          res: XGetBalanceRes = xBatchContractCallResToBalance(req.address, req.tokens, callRes)
        } yield {
          res.copy(
            balanceMap = res.balanceMap +
              (zeroAddress → BigInt(Numeric.toBigInt(ethRes.result)))
          )
        }) pipeTo sender
      }

    case req: XGetAllowanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetAllowanceReq:$req caused by invalid ethereum address")
        sender ! XGetAllowanceRes()
          .withAddress(req.address)
          .withError(XError()
            .withCode(XErrorCode.ETHEREUM_ERR_ILLEGAL_ADDRESS)
            .withMessage(s"invalid address in XGetBalanceAndAllowancesReq:$req"))
      } else {
        val batchReqs: XBatchContractCallReq = xGetAllowanceToBatchReq(Address(delegateAddress), req)
        (for {
          callRes ← (ethereumConnectionActor ? batchReqs).mapTo[XBatchContractCallRes]
          res: XGetAllowanceRes = xBatchContractCallResToAllowance(req.address, req.tokens, callRes)
        } yield res) pipeTo sender
      }

    case req: GetFilledAmountReq ⇒ //todo：订单的成交金额
  }

}
