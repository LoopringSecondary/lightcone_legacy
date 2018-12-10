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
import org.loopring.lightcone.actors.ethereum.EthereumConnectionActor
import org.loopring.lightcone.ethereum.EthReqManger
import org.loopring.lightcone.lib._
import org.loopring.lightcone.proto.actors._
import org.web3j.utils.Numeric
import org.loopring.lightcone.ethereum.data.Address

import scala.concurrent._

object AccountBalanceActor {
  val name = "account_balance"

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ XGetBalanceAndAllowancesReq(address, _) ⇒ (address, msg)
    case msg @ XSubmitOrderReq(Some(xorder)) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
    case msg @ XStart(_) ⇒ ("address_1", msg) //todo:该数据结构并没有包含sharding信息，无法sharding
  }

  private val extractShardId: ShardRegion.ExtractShardId = {
    case XGetBalanceAndAllowancesReq(address, _) ⇒ address
    case XSubmitOrderReq(Some(xorder)) ⇒ "address_1"
    case XStart(_) ⇒ "address_1"
  }

  def startShardRegion(delegateAddress: String)(
    implicit
    system: ActorSystem,
    config: Config,
    ec: ExecutionContext,
    timeProvider: TimeProvider,
    timeout: Timeout,
    actors: Lookup[ActorRef]
  ): ActorRef = {
    ClusterSharding(system).start(
      typeName = name,
      entityProps = Props(new AccountBalanceActor(actors, delegateAddress)),
      settings = ClusterShardingSettings(system),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
  }
}

class AccountBalanceActor(
    val actors: Lookup[ActorRef],
    val delegateAddress: String
)(
    implicit
    ec: ExecutionContext,
    timeout: Timeout
)
  extends Actor
  with ActorLogging {

  protected def ethereumConnectionActor: ActorRef = actors.get(EthereumConnectionActor.name)

  val ethReqManger = EthReqManger()
  val zeroAddress: String = "0x" + "0" * 40

  def receive: Receive = LoggingReceive {
    // TODO(dongw): even if the token is not supported, we still need to return 0s.
    case req: XGetBalanceAndAllowancesReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceAndAllowancesReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceAndAllowancesRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetBalanceAndAllowancesReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchBalanceReq = ethReqManger.packBatchBalanceCallReq(Address(req.address), tokens.map(Address(_)))
        val batchAllowanceReq = ethReqManger.packBatchAllowanceCallReq(Address(req.address), tokens.map(Address(_)), _spender = Address(delegateAddress))
        (for {
          balances ← (ethereumConnectionActor ? batchBalanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
          allowances ← (ethereumConnectionActor ? batchAllowanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ BigInt(Numeric.toBigInt(res.result))))
          ethBalance ← ethToken match {
            case Some(_) ⇒ (ethereumConnectionActor ? XEthGetBalanceReq(address = Address(req.address).toString, tag = "latest"))
              .mapTo[XEthGetBalanceRes]
              .map(res ⇒ Some(BigInt(Numeric.toBigInt(res.result))))
            case None ⇒
              Future.successful(None)
          }
        } yield {
          val balanceAndAllowance =
            (balances zip allowances)
              .map(ba ⇒ XBalanceAndAllowance(ba._1, ba._2))
          ethBalance match {
            case Some(ether) ⇒
              XGetBalanceAndAllowancesRes(
                req.address,
                (tokens zip balanceAndAllowance).toMap + (ethToken.get → XBalanceAndAllowance(ether, BigInt(0)))
              )
            case None ⇒
              XGetBalanceAndAllowancesRes(
                req.address,
                (tokens zip balanceAndAllowance).toMap
              )
          }
        }) pipeTo sender
      }
    case req: XGetBalanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetBalanceReq:$req caused by invalid ethereum address")
        sender ! XGetBalanceRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetBalanceReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchBalanceReq = ethReqManger.packBatchBalanceCallReq(Address(req.address), tokens.map(Address(_)))
        (for {
          balances ← (ethereumConnectionActor ? batchBalanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))))
          ethBalance ← ethToken match {
            case Some(_) ⇒ (ethereumConnectionActor ? XEthGetBalanceReq(address = Address(req.address).toString, tag = "latest"))
              .mapTo[XEthGetBalanceRes]
              .map(res ⇒ Some(BigInt(Numeric.toBigInt(res.result))))
            case None ⇒
              Future.successful(None)
          }
        } yield {
          ethBalance match {
            case Some(ether) ⇒
              XGetBalanceRes(
                req.address,
                (tokens zip balances).toMap + (ethToken.get → ether)
              )
            case None ⇒
              XGetBalanceRes(
                req.address,
                (req.tokens zip balances).toMap
              )
          }
        }) pipeTo sender
      }
    case req: XGetAllowanceReq ⇒
      if (!Address.isValid(req.address) || !req.tokens.forall(Address.isValid)) {
        log.error(s"invalid XGetAllowanceReq:$req caused by invalid ethereum address")
        sender ! XGetAllowanceRes()
          .withAddress(req.address)
          .withError(XError(error = s"invalid address in XGetAllowanceReq:$req"))
      } else {
        val tokens = req.tokens.filterNot(token ⇒ Address(token).toString.equals(zeroAddress))
        val ethToken = req.tokens.find(token ⇒ Address(token).toString.equals(zeroAddress))
        val batchAllowanceReq = ethReqManger.packBatchAllowanceCallReq(Address(req.address), tokens.map(Address(_)), _spender = Address(delegateAddress))
        (for {
          allowances ← (ethereumConnectionActor ? batchAllowanceReq)
            .mapTo[XBatchContractCallRes]
            .map(_.resps)
            .map(_.map(res ⇒ ByteString.copyFrom(Numeric.hexStringToByteArray(res.result))))
        } yield {
          ethToken match {
            case Some(address) ⇒
              XGetAllowanceRes(
                req.address,
                (req.tokens zip allowances).toMap + (address → BigInt(0))
              )
            case None ⇒
              XGetAllowanceRes(
                req.address,
                (req.tokens zip allowances).toMap
              )
          }
        }) pipeTo sender
      }
    case msg ⇒
      log.error(s"unsupported msg send to AccountBalanceActor:$msg")
      sender ! XError(error = s"unsupported msg send to AccountBalanceActor:$msg")
  }

}
